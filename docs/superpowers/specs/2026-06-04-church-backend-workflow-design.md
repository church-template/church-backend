# church-backend 구현 워크플로우 설계

> 작성일: 2026-06-04
> 출처 스펙: [`docs/church-backend-spec.md`](../../church-backend-spec.md) (권위 있는 청사진)
> 목적: 스캐폴드 상태의 백엔드를 **페이즈 단위로 분해**하여 GitHub 마일스톤·이슈로 추적 가능한 구현 로드맵을 정의한다.

## 현재 상태

- **스캐폴드**: `ChurchBackendApplication` + 컨텍스트 로드 테스트 + 기본 `application.properties`만 존재.
- **스택**: Spring Boot 4.0.6 / Java 21. 의존성은 actuator·data-jpa·data-redis·security·validation·webmvc·lombok·postgresql 까지 존재.
- **추가 필요 의존성**: springdoc-openapi(Swagger), JWT(jjwt 등), DB 마이그레이션(Flyway), 통합테스트(Testcontainers).
- **SB4 주의**: 웹 스타터는 `spring-boot-starter-webmvc`, 테스트는 모듈별 `*-test` 스타터.

## 의존성 원칙

- **Global → 도메인 단방향**. Global 기반이 모든 도메인의 blocker.
- RBAC 내부 순서: `position → role → member → auth`
  - member가 position·role FK 참조, auth가 member·role·security 의존.
- 콘텐츠 공통(`tag`·`media`)이 6개 콘텐츠 도메인의 선행 조건.
- `/api/main` 통합·캐싱은 sermon·notice·event 완료 후.

```
Phase 1 (Global)  ──blocker──>  Phase 2 (RBAC: position→role→member→auth)
                                        │
                                        ▼
                              Phase 3 (tag · media)
                                        │
                                        ▼
            Phase 4 (sermon · notice · event · department · gallery · bulletin)  [상호 병렬]
                                        │
                                        ▼
                              Phase 5 (/api/main · 캐싱 · 마무리)
```

## Phase별 이슈 (총 17개)

### Phase 1 — Global 기반 (4) · blocker · 내부 병렬 가능

| # | 이슈 | 스코프 | 스펙 |
|---|---|---|---|
| G1 | 빌드·환경·인프라 부트스트랩 | deps 추가(springdoc·jjwt·flyway·testcontainers), `application.yml`(`${ENV}`+프로파일, Swagger 토글), `.env.example`+`.gitignore`, `docker-compose`(pg16·redis7·backend, healthcheck·named volume) | §1,§10,§11 |
| G2 | 공통 모듈·예외 | `BaseEntity`(@MappedSuperclass: 감사·soft delete·`@Version`), Page 응답 래퍼, JpaConfig(Auditing), RFC 7807 `@RestControllerAdvice`+에러코드 매핑 | §5,§6,§7 |
| G3 | 보안 기반 | JWT 발급/검증 유틸, 인증 필터(클레임→권한), `SecurityConfig` 경로 3분법, RedisConfig, priority 위계 검증 유틸 | §4 |
| G4 | 파일 저장 | `FileStorage` 인터페이스 + `LocalFileStorage` 구현 | §8 |

### Phase 2 — 인증·인가 RBAC 코어 (4) · 순차(position→role→member→auth)

| # | 이슈 | 스코프 | 스펙 |
|---|---|---|---|
| D1 | position 도메인 | positions CRUD, 공개 목록(`sort_order ASC`), 삭제 시 `ON DELETE SET NULL` | §5.3 |
| D2 | role 도메인 | roles·permissions·role_permissions·member_roles, **시드 데이터**(permission 12종·role 4종·SUPER_ADMIN 1), 역할 CRUD·권한 일괄설정, 위계/`is_system`/마지막 SUPER_ADMIN 보호 | §3,§5.4 |
| D3 | member 도메인 | members·약관동의, `GET/PATCH /me`, 관리자 회원관리·reset-password, **역할 부여(=교인 승인)**·회수, 재동의 사이클(플래그 리셋), phone 부분 유니크 인덱스 | §5.2 |
| D4 | auth 도메인 | signup·login·refresh·logout, JWT payload(`sub`=uuid, flattened permissions+maxPriority), Redis refresh 저장·blacklist | §4,§5.1 |

### Phase 3 — 콘텐츠 공통 인프라 (2) · tag·media 병렬

| # | 이슈 | 스코프 | 스펙 |
|---|---|---|---|
| D5 | tag 도메인 | tags·content_tags(polymorphic), CRUD, 콘텐츠 연결(`tagIds`)·필터(`?tagId=`), 앱레벨 참조무결성 | §5.11 |
| D6 | media 도메인 | 중앙 라이브러리(image/PDF 업로드·목록·단건·공개 서빙), **references 추적 UNION 쿼리**, 차단형 삭제 `409 MEDIA_IN_USE`(+참조 목록) | §5.10 |

### Phase 4 — 콘텐츠 도메인 (6) · 상호 병렬 · 각각 member·tag·media 의존

| # | 이슈 | 스코프 | 스펙 |
|---|---|---|---|
| D7 | sermon | CRUD, 마크다운 TEXT, tags, `media:{id}`, view_count, 낙관적 락, 작성자=`updated_by`, `preached_at DESC` | §5.5 |
| D8 | notice | CRUD, `is_pinned` 우선 정렬, tags, media, 낙관적 락 | §5.7 |
| D9 | event | CRUD, `?year=&month=`·`?startDate=&endDate=` 조회, tags, media, `start_at` 정렬 | §5.6 |
| D10 | department | CRUD, 자기참조 `parent_id` 계층, `sort_order`, media | §5.8 |
| D11 | gallery | albums+photos, **GALLERY_VIEW 회원전용 조회**, media 재사용, tags, 대표=첫 사진(`sort_order`), 연결 해제 | §5.12 |
| D12 | bulletin | PDF media 연결, `service_date DESC`, 공개 조회 | §5.13 |

### Phase 5 — 통합·마무리 (1)

| # | 이슈 | 스코프 | 스펙 |
|---|---|---|---|
| D13 | 메인 통합·캐싱·배포 검증 | `/api/main`(최신 설교+공지+다가오는 일정) Redis `@Cacheable`/`@CacheEvict`, view_count 주기 반영, 통합테스트·Swagger 마감·새 교회 배포 체크리스트 검증 | §5.9,§9,§12 |

## 이슈 생성 메타

- **마일스톤**: `Phase 1: Global 기반` ~ `Phase 5: 통합·마무리` (5개), 각 이슈에 매핑.
- **라벨**: `작업전`.
- **담당자**: 미지정.
- **본문 형식**: 레포 `feature_request` 템플릿 구조(현재 문제점 / 해결방안 / 작업내용 체크리스트 / 참조 스펙 / 의존 이슈).
- **생성 수단**: GitHub MCP (`gh` CLI 미설치).

## 각 도메인 구현 시 공통 규약 (스펙 횡단)

모든 도메인 이슈는 아래를 일관 적용한다(상세는 스펙·`CLAUDE.md`):

- 패키지: `domain/<name>/{controller,service,repository,entity,dto}`, 단순 도메인은 평면 구조.
- soft delete(`deleted_at`) + 부분 인덱스(`WHERE deleted_at IS NULL`).
- 수정 가능 콘텐츠는 `BaseEntity` 상속(감사·낙관적 락).
- 인가는 권한 단위 `@PreAuthorize("hasAuthority('...')")`, 경로 3분법.
- 목록 응답 `{content, page}`, 에러 응답 RFC 7807, 본문 마크다운 원본 저장.
- 코드용 키는 영어, 사용자 노출 데이터는 한글.
- 도메인별 TDD(테스트 우선), 80% 커버리지 목표.

## 다음 단계

1. 본 문서 커밋.
2. GitHub 마일스톤 5개 + 이슈 17개 생성.
3. 이후 각 이슈는 Phase 순서/의존성에 따라 개별 브랜치·PR로 구현(TDD).
