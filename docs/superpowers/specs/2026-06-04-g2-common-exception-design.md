# G2 · Global 공통 모듈 · RFC 7807 예외 처리 설계

> 작성일: 2026-06-04
> 대상 이슈: `.issues/20260604_기능추가_G2_공통모듈_예외처리.md` (로드맵 Phase 1, 선행 의존 없음)
> 출처 스펙: [`docs/church-backend-spec.md`](../../church-backend-spec.md) §5·§6·§7
> 상위 로드맵: [`2026-06-04-church-backend-workflow-design.md`](./2026-06-04-church-backend-workflow-design.md)
> 선행: [#2 부트스트랩](./2026-06-04-phase1-bootstrap-design.md) (Flyway `validate` 정책이 본 설계의 테스트 전략에 영향)

## 목표 / 성공 기준

모든 도메인이 상속·재사용할 **횡단 공통 토대**를 한 번에 못 박는다. 도메인 엔티티·컨트롤러·서비스는 일절 만들지 않는다(`global/common`, `global/config/JpaConfig`, `global/exception`만 다룬다).

성공 기준:
1. 수정가능 콘텐츠 엔티티가 상속할 `BaseEntity`(+`BaseTimeEntity`)가 존재하고, 감사 타임스탬프가 persist 시 자동 채워진다.
2. **G2 범위의 컨트롤러·도메인 계층** 실패가 단일 `@RestControllerAdvice`를 통해 RFC 7807 형식(`errorCode·title·status·detail·instance`)으로 일관 매핑된다. (Security 필터 유래 401/403은 본 이슈 범위 밖 — #4에서 동일 `ErrorResponse`로 처리, 아래 D5·범위 경계 참조)
3. 목록 응답 표준이 스펙 JSON(`content` + `page{size,number,totalElements,totalPages}`)과 일치한다.
4. `global → domain` 의존이 0이다(ArchUnit `ArchitectureTest` green 유지).
5. 빌드·테스트 green(`./gradlew build`), 신규 코드 테스트 커버리지 80%+.

## 핵심 결정

| # | 결정 | 근거 |
|---|---|---|
| D1 | **BaseEntity 2단 분리**: `BaseTimeEntity`(createdAt) → `BaseEntity`(+updatedAt·createdBy·updatedBy·deletedAt·version) | 스펙 엔티티별 컬럼 매트릭스상 단일 base는 마스터·회원 데이터에 미사용 컬럼(version·author·updatedAt)을 강제. 2단이 미사용 최소화 ↔ 단순성 균형(§5 작성자·락 정책, §6 인덱스, §7). `updatedAt`을 아래(BaseEntity)에 둔 근거는 매트릭스 절 참조 |
| D2 | **작성자 컬럼은 `Long`** (Member 연관 아님) | `global/common`이 `domain.member`를 참조하면 `global→domain` 역전 → `ArchitectureTest` 빌드 실패. id→이름·"(탈퇴한 사용자)" 변환은 각 도메인 쿼리 계층 책임(§5 작성자 정책) |
| D3 | **목록 응답 = Spring Data `PagedModel<T>` 채택** (커스텀 X) | `PagedModel`이 스펙 JSON과 바이트 단위 동일. `Page` 직접 직렬화 비권장의 안정 대체물. battle-tested 우선 원칙 |
| D4 | **예외 바디 = 커스텀 `ErrorResponse` record** (Spring `ProblemDetail` X) | `ProblemDetail`은 `type` 필드를 강제하고 `errorCode`가 1급 필드가 아니라 스펙 모양과 불일치(§5 에러 형식) |
| D5 | **보안 유래 3개 코드는 "정의만, 배선은 #4"** | `AUTHENTICATION_FAILED·INVALID_TOKEN·ACCESS_DENIED`는 Security 필터 체인에서 발생 → `@RestControllerAdvice` 포착 불가. 엔트리포인트/핸들러는 #4에서 본 ErrorCode 재사용 |
| D6 | **JPA Auditing 배선은 지금, 작성자 값 채움은 #4** | `@EnableJpaAuditing` + 스텁 `AuditorAware<Long>`(`Optional.empty()`). SecurityContext 미존재 → 작성자 null. #4가 AuditorAware 본문만 교체("배관은 지금, 물은 #4") |

## BaseEntity 근거 — 스펙 엔티티별 컬럼 매트릭스

| 엔티티 | created_at | updated_at | deleted_at | created_by/updated_by | version | 상속 |
|---|:---:|:---:|:---:|:---:|:---:|---|
| position / role / tag | ✓ | ✗ | ✗ 하드삭제 | ✗ | ✗ | `BaseTimeEntity` (createdAt만) |
| media / gallery_photos | ✓ | ✗ | ✗ | uploaded_by(자체) | ✗ | `BaseTimeEntity` (createdAt만) |
| member | ✓ | ✗ | ✓ | ✗ | ✗ | `BaseTimeEntity` + `deletedAt` 자체 선언 |
| event / department | ✓ | ✗ 스펙표 | ✓ | ✗(§5 작성자 정책서 제외) | ✓ | `BaseEntity` (↓ 의도적 편차) |
| sermon / notice / gallery_album / bulletin | ✓ | ✓ | ✓ | ✓ | ✓ | `BaseEntity` (정확히 일치) |

> 위 매트릭스는 스펙 §3.2·§5의 **테이블별 실제 컬럼**을 엄격히 따른다. `updated_at`은 스펙 스키마상 수정가능 콘텐츠 4종(설교·공지·갤러리앨범·주보)에만 존재한다.

- **`updatedAt`은 `BaseTimeEntity`가 아니라 `BaseEntity`에 둔다(리뷰 반영, Finding 1).** `BaseTimeEntity`에 `updated_at`을 두면 master/member/media처럼 스펙표에 `updated_at`이 없는 엔티티에 컬럼이 강제돼, 도메인 Flyway 마이그레이션 ↔ `ddl-auto=validate` 충돌을 부른다. 그래서 **`BaseTimeEntity = createdAt`만** 둔다.
- `deletedAt`도 `BaseTimeEntity`가 아니라 `BaseEntity`에 둔다: position/role/tag는 하드삭제(§6 인덱스에 `WHERE deleted_at IS NULL` 없음). 위에 두면 마스터 데이터에 미사용 `deleted_at`이 붙는다. 소프트삭제가 필요한 `member`만 한 줄 자체 선언.
- **의도적 편차 — event/department:** §216상 두 도메인은 수정가능 + 낙관락(version) 콘텐츠라 `BaseEntity`를 상속하며, 그 결과 스펙표엔 없는 `updated_at` + nullable `created_by/updated_by`를 얻는다. 편집되는 엔티티가 `updated_at`을 갖는 건 오히려 정합적이고(스펙표의 누락은 비망라적 약식 표기로 판단), **D9/D10 마이그레이션은 이 컬럼들을 포함해야 한다**(§211 작성자 표시 정책이 그 컬럼을 안 읽을 뿐).
- 검토했으나 안 채택한 대안: 4단 순수 체인(createdAt ⊂ +deletedAt ⊂ +version ⊂ +updatedAt·작성자). 편차·미사용 컬럼 0이지만 base 상속 4단은 간접층 과다 → YAGNI상 과설계로 판단, 2단 유지. 편차가 거슬리면 이 대안으로 전환 가능.

## 산출물

| 파일 | 내용 |
|---|---|
| `global/common/BaseTimeEntity.java` | `@MappedSuperclass` + `@EntityListeners(AuditingEntityListener.class)`. **`@CreatedDate createdAt`만** (`LocalDateTime`, 단일 교회·KST 전제). 리스너는 서브클래스가 상속하므로 `BaseEntity`의 `@LastModified*`도 함께 처리됨 |
| `global/common/BaseEntity.java` | `extends BaseTimeEntity`. **`@LastModifiedDate updatedAt`**, `@CreatedBy createdBy:Long`, `@LastModifiedBy updatedBy:Long`, `deletedAt:LocalDateTime`, `@Version version:Long`. `isDeleted()` 정도의 최소 헬퍼만 |
| `global/config/JpaConfig.java` | `@Configuration @EnableJpaAuditing(auditorAwareRef="auditorAware")` + 스텁 `AuditorAware<Long>` 빈(`Optional.empty()`) |
| `global/config/WebConfig.java` *(또는 기존 설정 확장)* | `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` — `Page<T>` 반환을 `PagedModel` JSON으로. (정확한 SB4 활성화 문법은 구현 시 Context7 확인) |
| `global/exception/ErrorCode.java` | enum `(HttpStatus status, String code, String title)`. 스펙 8개 + 폴백 `INTERNAL_ERROR` |
| `global/exception/ErrorResponse.java` | record `{errorCode, title, status, detail, instance}` + 선택 `errors`/`references`(`@JsonInclude(NON_NULL)`). `of(ErrorCode, instance, ...)` 정적 팩토리 |
| `global/exception/BusinessException.java` | `extends RuntimeException`, `ErrorCode` 보유. 선택 detail 오버라이드·추가 데이터(references) 수용 |
| `global/exception/GlobalExceptionHandler.java` | `@RestControllerAdvice`. 아래 매핑표 |
| `src/test/.../BaseEntityAuditingTest.java` | `@DataJpaTest` + Testcontainers·`replace=NONE`·create-drop 격리(↓테스트 전략) + `BaseEntity` 상속 테스트 엔티티 → createdAt/updatedAt 검증 |
| `src/test/.../GlobalExceptionHandlerTest.java` | `@WebMvcTest` + 테스트 전용 더미 컨트롤러 → 예외별 JSON 검증 |
| `src/test/.../PagedModelSerializationTest.java` | `PageImpl`→`PagedModel` Jackson 직렬화 → 스펙 JSON 모양 단언 |

**G2는 Flyway 마이그레이션을 0개 추가한다.** `BaseTimeEntity`/`BaseEntity`는 `@MappedSuperclass`라 테이블이 없고, 나머지도 테이블이 없다. 실제 테이블은 전부 도메인 이슈에서 `Vn__*.sql`로 추가된다.

## ErrorCode enum (단일 진실 공급원)

| status | code | title(한글) | G2 배선 |
|---|---|---|---|
| 400 | `INVALID_INPUT_VALUE` | 유효하지 않은 입력값 | ✅ 검증 예외 매핑 |
| 401 | `AUTHENTICATION_FAILED` | 인증에 실패했습니다 | ⏸ 정의만 (#4 배선) |
| 401 | `INVALID_TOKEN` | 유효하지 않은 토큰입니다 | ⏸ 정의만 (#4 배선) |
| 403 | `ACCESS_DENIED` | 접근 권한이 없습니다 | ⏸ 정의만 (#4 배선) |
| 404 | `RESOURCE_NOT_FOUND` | 리소스를 찾을 수 없습니다 | ✅ BusinessException |
| 409 | `MEDIA_IN_USE` | 사용 중인 미디어입니다 | ✅ BusinessException(+references) |
| 409 | `OPTIMISTIC_LOCK_CONFLICT` | 다른 사용자가 먼저 수정했습니다 | ✅ 락 예외 매핑 |
| 409 | `DUPLICATE_RESOURCE` | 이미 존재하는 리소스입니다 | ✅ BusinessException |
| 500 | `INTERNAL_ERROR` | 서버 오류가 발생했습니다 | ✅ 폴백(스택 로깅·상세 비노출) |

> 스펙 8개 + `INTERNAL_ERROR` 1개. `INTERNAL_ERROR`는 스펙 외 추가지만 미처리 예외의 안전 폴백으로 필수(상세 비노출로 정보 누출 방지). `.claude/rules/api-conventions.md`의 "코드 새로 만들지 말 것"은 임의 남발 금지 취지이며, 표준 500 폴백은 예외.

## GlobalExceptionHandler 매핑 (G2 범위)

| 예외 | → errorCode (status) | 비고 |
|---|---|---|
| `BusinessException` | 내부 `ErrorCode` 그대로 | RESOURCE_NOT_FOUND·DUPLICATE_RESOURCE·MEDIA_IN_USE 등 도메인이 throw |
| `MethodArgumentNotValidException` / `HandlerMethodValidationException` / `ConstraintViolationException` | `INVALID_INPUT_VALUE` (400) | 필드 오류를 `errors` 배열로 동봉 |
| `ObjectOptimisticLockingFailureException` / `OptimisticLockException` | `OPTIMISTIC_LOCK_CONFLICT` (409) | @Version 충돌 |
| 그 외 `Exception` | `INTERNAL_ERROR` (500) | 스택트레이스 로깅, `detail`은 일반 문구(원인 비노출) |

- `instance`는 `HttpServletRequest`의 요청 URI로 채운다.
- `DataIntegrityViolationException`은 **자동 매핑하지 않는다** — DB 세부 누출·과잉 일반화 위험. 중복은 도메인이 사전 검증 후 `BusinessException(DUPLICATE_RESOURCE)`를 명시적으로 throw.

### 범위 경계 (G2에서 하지 않는 것 = #4 보안 기반)
- `AuthenticationEntryPoint`(401) / `AccessDeniedHandler`(403) 구현 및 SecurityConfig 배선.
- `AuthenticationException`·`AccessDeniedException`의 RFC 7807 변환(필터 레벨이라 advice 밖).
- `AuditorAware`의 실제 SecurityContext 조회(현재는 스텁).
- → 셋 다 본 설계의 `ErrorCode`·`ErrorResponse`·`AuditorAware`를 **재사용**해 #4에서 완성.

## 테스트 전략 (TDD, RED→GREEN)

| 대상 | 방식 | 핵심 단언 |
|---|---|---|
| BaseEntity 감사 | `@DataJpaTest`(아래 격리 설정) + `BaseEntity` 상속 테스트 엔티티 persist/flush | `createdAt`·`updatedAt` non-null, `version` 0 시작. 작성자는 #4까지 null |
| GlobalExceptionHandler | `@WebMvcTest` + 테스트 전용 더미 컨트롤러가 각 예외 throw | 응답 status·`errorCode`·`title`·`instance` 일치, 검증 실패 시 `errors` 존재 |
| PagedModel 직렬화 | `ObjectMapper`로 `PagedModel`(PageImpl 래핑) 직렬화 | `content` 배열 + `page.{size,number,totalElements,totalPages}` 정확 |
| 아키텍처 | 기존 `ArchitectureTest` | `global→domain` 의존 0 유지 |

**`@DataJpaTest` 데이터소스 + Flyway `validate` 충돌 처리(중요, 리뷰 Finding 3 반영):** 프로젝트엔 임베디드 DB(H2 등)가 없고 PostgreSQL/Testcontainers 기반이며(`build.gradle` 확인), #2가 `ddl-auto=validate` + Flyway 스키마 소유로 고정했다. 따라서 감사 슬라이스 테스트는 다음을 **모두** 갖춰야 동작한다.
- `@AutoConfigureTestDatabase(replace = Replace.NONE)` — `@DataJpaTest` 기본값(임베디드로 데이터소스 교체)을 끈다. 없으면 "임베디드 DB 없음"으로 데이터소스 자체를 못 잡는다.
- `@Import(TestcontainersConfiguration.class)` — `@ServiceConnection` PostgreSQL 컨테이너를 실제 데이터소스로 주입(기존 통합테스트와 동일 패턴).
- `@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})` — 테스트 전용 엔티티는 마이그레이션이 없어 `validate`가 실패하므로, 이 슬라이스에서만 Hibernate가 테스트 테이블을 생성하게 우회(메인 정책 불변, 테스트만).

## 의존성 / 빌드 영향
- 신규 외부 의존성 **없음**. 모두 이미 클래스패스(data-jpa = auditing·`@Version`, webmvc = advice, spring-data-commons = `PagedModel`, validation = 검증 예외, lombok = 보일러플레이트).
- 후속 영향: 모든 도메인 이슈(D1~D13)가 본 `BaseEntity`·`ErrorCode`·`PagedModel` 규약에 의존. 본 설계가 도메인들의 일관성 기준점.

## 미해결 / 구현 시 확인
1. `@EnableSpringDataWebSupport(pageSerializationMode=VIA_DTO)`의 SB4 정확 문법·위치 — Context7로 확인.
2. 감사 타임스탬프 타입 `LocalDateTime` vs `Instant` — 단일 교회·KST 전제로 `LocalDateTime` 채택(스펙 "TIMESTAMP"와 정합). 추후 다지역 필요 시 교체.
3. `ErrorResponse`의 `references` 필드를 공통 record에 둘지, MEDIA_IN_USE 전용 서브타입으로 분리할지 — 우선 공통 record + `@JsonInclude(NON_NULL)`로 단순화.
