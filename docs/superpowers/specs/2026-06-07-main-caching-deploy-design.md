# #18 메인 통합 API · Redis 캐싱 · view_count · 배포 검증 — 설계

- **이슈:** #18 (메인 통합 API / Redis 캐싱 / 배포 종단 검증)
- **브랜치:** `20260603_#18_메인_통합_API_Redis_캐싱_배포_검증`
- **스펙 근거:** `docs/church-backend-spec.md` §5.9(공통 `/api/main`), §9(Redis 사용처), §10–§12(환경변수·인프라·배포 체크리스트), §1(Swagger), §5(에러표)
- **성격:** Phase 5 마무리. #1–17 전 도메인 구현 완료를 전제로, 미구현 신규 3종 + 검증에서 드러난 갭을 닫는다.

---

## 0. 배경 — 검증 결과 요약

17개 영역(12 도메인 + 5 횡단) 병렬 감사 결과:

- **기능(#1–17):** 12개 도메인 전부 기능적으로 COMPLETE/MINOR. 엔드포인트·필드·인가·경로 3분법·위계검증·차단삭제 등 핵심 불변식 구현·테스트 완료. **기능 갭 없음.**
- **신규 미구현(#18 핵심):** `GET /api/main`, Redis 캐싱(`@EnableCaching`/`CacheConfig`/`@Cacheable`/`@CacheEvict`), view_count 주기 플러시 — 모두 미구현.
- **view_count 설계편차:** 현재 상세조회마다 동기 원자 `UPDATE`(작동은 함). 스펙 §9의 "Redis 카운팅 후 주기적 DB 반영" 아키텍처 아님.
- **예외(횡단):** 코어 RFC 7807는 견고하나 `MethodArgumentTypeMismatchException`이 매핑 누락 → 타입변환 실패가 500으로 누수.
- **Swagger(횡단, 최대 갭):** 인프라(springdoc 3.0.0, `OpenApiConfig` 전역 Info+bearerAuth, `SWAGGER_ENABLED` 토글)는 있으나 **메서드레벨 문서 전무** — 24개 컨트롤러 / 71개 엔드포인트 / 59개 DTO에 `@Operation`/`@ApiResponse`/`@Tag`/`@Schema` 0건.
- **인프라(CRITICAL):** `docker-compose.yml` backend `environment:`가 `ADMIN_PHONE/NAME/PASSWORD`를 누락(allowlist, `env_file:` 없음) → `docker compose up` 시 SUPER_ADMIN 시드가 항상 조용히 스킵. §12-5 배포검증 불가.

**확정된 큰 결정(사용자 승인):**
1. 범위 = 신규 3종 + 갭보완 전부(Swagger 전 도메인 + 예외 핸들러 + compose/SUPER_ADMIN).
2. view_count = Redis 버퍼 + `@Scheduled` 플러시(스펙 §9 준수).
3. Swagger = 공통 에러응답 일괄주입(`OperationCustomizer`) + 엔드포인트 수기.

---

## 1. 범위 & 비범위

**포함:** D-1 `GET /api/main` · D-2 Redis 캐싱 · D-3 view_count Redis 버퍼+스케줄 · D-4 예외(타입변환) 핸들러 · D-5 docker-compose SUPER_ADMIN 주입 · D-6 Swagger 전 도메인 문서화 · D-7 테스트.

**비범위:**
- 갤러리 caption 입력경로(별도 후속 이슈로 이미 선언됨).
- Tag 데드코드(`TagRepository.existsByNameAndIdNot` 미사용) 제거 — 선택 LOW, 본 이슈에서 정리 가능하나 필수 아님.
- 마지막 SUPER_ADMIN 회수 check-then-act 레이스(보고서에서 보류 결정).
- **DB 마이그레이션 없음** — 스키마 변경 0. `view_count` 컬럼(설교·공지)·events `(start_at) WHERE deleted_at IS NULL` 부분인덱스(V9) 이미 존재.

---

## 2. D-1. `GET /api/main` (공개) — §5.9

신규 패키지 `com.elipair.church.domain.main`:

| 파일 | 역할 |
|---|---|
| `MainController` | `GET /api/main` (공개; SecurityConfig `anyRequest().permitAll()`로 충족, 추가 보안 배선 불필요) |
| `MainService` | 도메인 서비스 조합 |
| `dto/MainResponse` | `{ sermons, notices, upcomingEvents }` |

- **조합:** `MainService`가 기존 서비스를 재사용한다.
  - `SermonService.list(null, null, null, null, null, null, PageRequest.of(0, 3))` → 최신 설교 3건(카드)
  - `NoticeService.list(null, null, PageRequest.of(0, 3))` → 공지 3건(고정 우선, 카드)
  - 신규 `EventService.upcoming(5)` → 다가오는 일정 5건(카드)
- `MainResponse`는 기존 카드 DTO(`SermonCardResponse`·`NoticeCardResponse`·`EventCardResponse`)를 그대로 담는다(본문 `content` 제외 정책 자동 준수).
- **신규 쿼리** `EventRepository.findUpcoming(LocalDateTime now, Pageable)`:
  - `WHERE start_at >= :now AND deleted_at IS NULL ORDER BY start_at ASC`
  - 기존 `idx_events_start_at` 부분인덱스 사용.
- **확정 기본값:** "다가오는" = `start_at >= now`(미래 시작 기준). 카드 개수 = 설교 3 / 공지 3 / 일정 5.
- `now`는 `LocalDateTime.now()`. 테스트 용이성을 위해 `Clock` 주입은 선택(현 코드베이스가 Clock 미사용이므로 직접 호출 유지, 테스트는 now 기준 상대 시드).

---

## 3. D-2. Redis 캐싱 — §9

신규 `com.elipair.church.global.config.CacheConfig`:

- `@Configuration @EnableCaching`
- `RedisCacheManager` 빈:
  - 기본 TTL = `${CACHE_TTL:60}`초(env 주입 — 멀티-교회 규칙상 하드코딩 금지, [[multi-church-template]]).
  - 값 직렬화 JSON. **⚠️ SB4 = Jackson 3**: 직렬화기 좌표를 Context7로 확정한다(메모리의 `tools.jackson` 함정 — `GenericJackson2JsonRedisSerializer`가 Jackson 3에서 어떻게 바뀌는지 확인).
  - 캐시 이름: `main`, `sermonListFirstPage`.

**캐시 적용:**
- `@Cacheable("main")` → `MainService.getMain()`.
- `@Cacheable(value = "sermonListFirstPage", key = "...", condition = "...")` → `SermonService.list(...)`.
  - **조건:** 필터 전부 null(`preacher`·`series`·`from`·`to`·`q`·`tagId`) **그리고** `pageable.pageNumber == 0` 일 때만 캐싱(키 폭발 방지 — 홈/첫 페이지 공개 목록만).
  - 키에 `size`·`sort`를 반영(다른 size/sort 요청이 충돌하지 않게).

**무효화(`@CacheEvict`):**
- `SermonService` create/update/patch/delete → `@CacheEvict(value = {"main", "sermonListFirstPage"}, allEntries = true)`
- `NoticeService` create/update/patch/delete → `@CacheEvict(value = "main", allEntries = true)`
- `EventService` create/update/patch/delete → `@CacheEvict(value = "main", allEntries = true)`
- 근거: `/api/main`은 설교·공지·일정을 모두 담으므로 셋 중 어느 CUD든 `main` 무효화. 설교 목록 첫 페이지는 설교 CUD에만 의존.

**트레이드오프(문서화):** view_count 플러시(D-3)는 캐시를 evict하지 않는다. 목록 카드의 조회수는 최대 TTL(`CACHE_TTL`, 기본 60s)만큼 stale할 수 있다 — 의도된 단순화. TTL이 안전망이므로 evict 누락 시에도 자동 만료된다.

---

## 4. D-3. view_count — Redis 버퍼 + `@Scheduled` 플러시 (§9 준수)

기존 미디어참조 **Provider SPI 패턴**과 동일한 결로, `global`이 도메인을 역참조하지 않게 설계(domain→global 단방향 유지, [[persistence-conventions]]).

**global 측(도메인-무관), 신규 패키지 `com.elipair.church.global.viewcount`:**
- `ViewCountStore` — Redis 연산 캡슐화. 키 = `view:{namespace}:{id}`.
  - `increment(String ns, long id)` → `INCR`
  - `currentDelta(String ns, long id)` → 현재 버퍼값(라이브 표시용; 없으면 0)
  - `drain(String ns)` → 해당 ns의 모든 키를 `SCAN`(`KEYS` 금지) 후 키마다 `GETDEL`(원자 get+삭제) → `Map<Long id, Long delta>` 반환
- `ViewCountFlushTarget` 인터페이스 — `String namespace()`, `void applyDeltas(Map<Long, Long> deltas)`.
- `ViewCountFlushScheduler` — `List<ViewCountFlushTarget>` 주입. `@Scheduled(fixedDelayString = "${view.flush-interval:60000}")`로 각 target에 대해 `store.drain(ns)` → `target.applyDeltas(...)`.
- `global/config/SchedulingConfig`(또는 메인 앱) — `@EnableScheduling`.

**domain 측(global SPI 구현):**
- `SermonViewCountFlushTarget`(namespace `"sermon"`) → `SermonRepository.incrementViewCountBy(id, delta)` 호출.
- `NoticeViewCountFlushTarget`(namespace `"notice"`) → `NoticeRepository.incrementViewCountBy(id, delta)`.
- 신규 repo 메서드: `@Modifying @Query("update Sermon s set s.viewCount = s.viewCount + :delta where s.id = :id and s.deletedAt is null") int incrementViewCountBy(id, delta)` (Notice 동일).
- 기존 동기 `incrementViewCount(id)`(+1)는 상세조회 경로에서 **제거**(플러시 잡 전용 bulk만 남김). 회귀 테스트가 참조하던 메서드는 `incrementViewCountBy`로 대체.

**상세조회 흐름 변경(Sermon·Notice `get(id)`):**
1. 존재 확인 — `findByIdAndDeletedAtIsNull` 없으면 `RESOURCE_NOT_FOUND`(존재하지 않는 글에 조회수 카운팅 방지).
2. `viewCountStore.increment(ns, id)` (Redis INCR).
3. 응답 `viewCount` = `entity.getViewCount() + viewCountStore.currentDelta(ns, id)` (DB 누적 + 미플러시 버퍼 = 라이브 값).
   - `@Transactional` 제거 가능(더 이상 DB write 안 함) — 단, 태그/작성자 조회가 같은 트랜잭션이면 `readOnly`로 유지.

**크래시 창:** 플러시 직전 미반영 증가분은 컨테이너 비정상 종료 시 최대 1주기 유실 가능 — §9 의도(부하 완화 우선)상 허용. rule/주석에 명시.

**신규 env:** `VIEW_FLUSH_INTERVAL`(ms, 기본 60000) — application.yml `view.flush-interval`, .env.example, compose 동기 추가.

---

## 5. D-4. 예외 — 타입변환 갭(전역 1회 수정)

`GlobalExceptionHandler`에 추가:
- `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` → **400 `INVALID_INPUT_VALUE`**.
  - 차단 대상: `GET /api/events?year=abc`·`?startDate=notadate`, `PATCH /api/admin/bulletins/{id}?version=abc`·`?serviceDate=오류` 등 모든 `@RequestParam`/`@PathVariable` 변환 실패.
- **추가(권장, 선택):** `MissingServletRequestParameterException`·`HandlerMethodValidationException`(SB4 메서드 검증)도 400으로 일관 매핑.
- 확장 ErrorCode 4종(`ROLE_IN_USE`·`DEPARTMENT_HAS_CHILDREN`·`FILE_SIZE_EXCEEDED`·`FILE_STORAGE_ERROR`)을 `api-conventions.md`/스펙 §5 표에 정식 등재(문서-only).

---

## 6. D-5. docker-compose / SUPER_ADMIN

`docker-compose.yml` backend `environment:`에 3줄 추가(기존 `${VAR:-default}` allowlist 스타일 유지):

```yaml
ADMIN_PHONE: ${ADMIN_PHONE:-}
ADMIN_NAME: ${ADMIN_NAME:-}
ADMIN_PASSWORD: ${ADMIN_PASSWORD:-}
```

- `.env`에는 이미 3종 추가됨(로컬 dev).
- 효과: `docker compose up`에서 ADMIN_* 값이 컨테이너 프로세스 환경으로 주입 → `SuperAdminInitializer`가 멱등 생성. 빈 값이면 기존대로 스킵(멱등성 유지).
- `.env.example`은 이미 3종 + `SWAGGER_ENABLED` 보유. 신규 env(`CACHE_TTL`·`VIEW_FLUSH_INTERVAL`)를 `.env.example`·compose에 추가.

---

## 7. D-6. Swagger 전 도메인 문서화 — 공통 일괄주입 + 수기

**공통(반복 제거):**
- `OpenApiConfig`에 `OperationCustomizer`(또는 `GlobalOpenApiCustomizer`) 빈 추가:
  - 모든 오퍼레이션에 공통 RFC 7807 응답을 주입: 400/401/403/404/409 → `content = ErrorResponse` `@Schema`.
  - 공개 GET(경로가 `/api/admin/**`·`/api/gallery/**`가 아닌 것)은 전역 bearerAuth 자물쇠 제거(`security = {}`) — LOW 갭(공개 GET에 자물쇠 표기) 해소.
- `ErrorResponse`(및 `ValidationError`·`references`)를 `@Schema`로 기술.

**수기(엔드포인트/DTO):**
- 컨트롤러 24개에 `@Tag(name, description)` — 도메인 단위 그룹핑(admin/public 분리 도메인은 동일 태그로 묶음).
- 엔드포인트 71개에 `@Operation(summary 한 줄 한글, description — 인가요건 + 부수효과(soft delete·낙관락·차단삭제 등) 명시)`.
- 핵심 Request DTO + 대표 Response DTO에 `@Schema(description, example)` — 규약성 필드 우선(phone `01012345678`, `media:{id}` 본문 참조, page/size/sort).
- **레퍼런스 우선:** 설교(`Sermon`·`AdminSermon`)에 표준 패턴을 먼저 정립 → 나머지 도메인에 전파. 우선순위 도메인: auth·member·sermon·notice·event·media.

**선택:** Swagger 경로 `/docs/swagger-ui.html` → 스펙 §1의 `/swagger-ui.html` 정렬 여부. **기본: 현행 유지.**

---

## 8. D-7. 테스트 (Testcontainers) — TDD

| 영역 | 검증 |
|---|---|
| MainService 조합 | 최신 N건 + upcoming 경계(now 시작=포함, 과거 시작=제외, ORDER BY start_at ASC). 빈 도메인 시 빈 배열. |
| 캐싱 | 동일 요청 2회 → DB 조회 1회(repo 스파이/카운트). 해당 도메인 CUD 후 재요청 → DB 재조회(evict 확인). |
| view_count 플러시 | 상세조회 N회 → Redis 버퍼 N → 플러시 → DB `view_count += N` + 키 삭제. 라이브 읽기 = DB + 버퍼. |
| 예외 | `?year=abc` 등 타입변환 → 400 `INVALID_INPUT_VALUE`(기존 논리오류 400 회귀 유지). |
| Swagger 스모크 | `/v3/api-docs` JSON에 대표 operationId·summary 존재 + `ErrorResponse` schema 노출 단언 1~2건. |

- 기존 `MigrationIndexTest` 패턴은 신규 인덱스 없으므로 추가 없음.
- 기존 view_count 회귀 테스트(`SermonServiceTest`·`SermonRepositoryTest`·`NoticeServiceTest`·`NoticeRepositoryTest`)는 새 흐름(Redis 버퍼 + bulk 플러시)에 맞게 수정.

---

## 9. D-8. 의존성 & 설정

- `@EnableCaching`·`@EnableScheduling` → spring-context(기존 classpath).
- `RedisCacheManager` → spring-data-redis(기존). **starter-cache 추가 필요 여부를 Context7로 확정**(아마 config만으로 충분 — data-redis가 캐시 매니저 제공).
- 신규 env 3종 배선(동기): `CACHE_TTL`(초, 기본 60), `VIEW_FLUSH_INTERVAL`(ms, 기본 60000).
  - `application.yml`: `cache.ttl`, `view.flush-interval`(또는 spring.cache.redis.time-to-live 활용).
  - `.env.example` / `docker-compose.yml` backend `environment:`.

---

## 10. 새 교회 배포 검증(§12) 절차

구현 후 종단 검증 시나리오(가능하면 통합 테스트/문서로):
1. `.env.example` → `.env` 복사, 비밀번호 3종 + `CORS_ALLOWED_ORIGIN` + `ADMIN_*` 교체.
2. `./gradlew build`.
3. `docker compose up -d --build` — postgres16/redis7 healthy 후 backend 기동.
4. `SuperAdminInitializer` 로그로 SUPER_ADMIN 생성 확인(`ADMIN_PHONE`으로 로그인 → 비밀번호 변경).
5. `GET /api/main` 200 + Swagger UI(`SWAGGER_ENABLED=true`) 노출 확인.

---

## 11. 파일 변경 요약

**신규:**
- `domain/main/MainController.java`, `MainService.java`, `dto/MainResponse.java`
- `global/config/CacheConfig.java`
- `global/config/SchedulingConfig.java`(또는 메인 앱에 `@EnableScheduling`)
- `global/viewcount/ViewCountStore.java`, `ViewCountFlushTarget.java`, `ViewCountFlushScheduler.java`
- `domain/sermon/SermonViewCountFlushTarget.java`, `domain/notice/NoticeViewCountFlushTarget.java`
- 테스트: Main 통합, 캐시, 플러시, 예외, Swagger 스모크

**수정:**
- `EventRepository`(findUpcoming), `EventService`(upcoming) + Event/Sermon/Notice 서비스 `@CacheEvict`
- `SermonService`·`NoticeService` `get()` 흐름(Redis 버퍼) + `SermonRepository`·`NoticeRepository`(incrementViewCountBy)
- `GlobalExceptionHandler`(타입변환 핸들러)
- `OpenApiConfig`(OperationCustomizer) + 24개 컨트롤러(@Tag/@Operation) + 핵심 DTO(@Schema)
- `docker-compose.yml`(ADMIN_*/신규 env), `application.yml`·`.env.example`(신규 env)
- 기존 view_count 회귀 테스트 수정

---

## 12. 확인 필요(구현 중 Context7)

- SB4 + Jackson 3 환경의 Redis 캐시 값 직렬화기 정확한 좌표/API.
- SB4에서 `RedisCacheManager` 사용 시 `spring-boot-starter-cache` 필요 여부.
- `@Scheduled` `fixedDelayString` + property 바인딩 SB4 동작 확인.
