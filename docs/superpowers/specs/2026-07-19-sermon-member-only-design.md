# 설교 회원전용 전환 설계

- 날짜: 2026-07-19
- 유형: 권한 정책 변경 (설교 조회 공개 → 회원전용)
- 발신 배경: church-frontend가 설교를 갤러리처럼 회원전용으로 전환하려 함. 이 문서는 그 백엔드 선행분.
- 프론트 후속: 이 변경 반영 → 백엔드 OpenAPI 재생성 → 프론트 `docs/api-docs.json` 교체 후 프론트 전환.

## 1. 목표

설교 조회 API(`GET /api/sermons`, `GET /api/sermons/{id}`)를 갤러리(`/api/gallery/**`)와 동일하게
**회원전용(교인 승인 후 열람)** 으로 전환한다. 신설 권한 `SERMON_VIEW`로 게이트한다.

- `SERMON_WRITE`(어드민 등록/수정/삭제)와는 **독립된 축** — 조회 권한을 별도로 신설한다.
- `MEMBER`(승인 교인) 이상만 열람. `USER`(가입 직후 기본)·익명은 차단.

## 2. 현재 상태 (코드 검증 완료)

- `SecurityConfig.securityFilterChain`: 매처 체인이 `/api/admin/**` → `authenticated`,
  `/api/gallery/**` → `hasAuthority("GALLERY_VIEW")`, `/api/bible-challenges/**` →
  `hasAuthority("CHALLENGE_PARTICIPATE")` 순으로 배치되고 **`anyRequest().permitAll()`** 로 끝남 → 설교 GET은 현재 공개.
- 권한 시드: `V2__create_rbac.sql`(12종) + `V13`(CHALLENGE 2종) + `V14`(INQUIRY_MANAGE 1종) = **현재 15종**.
  `MEMBER` 역할 = `GALLERY_VIEW` + `CHALLENGE_PARTICIPATE`. `SERMON_VIEW`는 존재하지 않음.
- 마이그레이션 최신 = `V14__create_inquiries.sql` → 신규는 **V15**.
- `SermonController`: `GET /api/sermons`(목록), `GET /api/sermons/{id}`(상세, 조회수 버퍼 +1 부수효과).
  클래스 Javadoc·두 `@Operation` description에 "비인증/인증 불필요" 명시(수정 대상).
- 어드민 쓰기: `AdminSermonController`(`/api/admin/sermons/**`, `SERMON_WRITE`) — 이번 변경과 무관.
- **OpenAPI**: `OpenApiConfig`가 **전역** bearerAuth `SecurityRequirement`를 모든 오퍼레이션에 부착함.
  즉 생성 스펙에는 공개/회원전용 구분이 **기계적 security 필드가 아니라 `@Operation` description 텍스트로만** 존재한다.
  리포에 커밋된 `api-docs.json`은 없음(런타임 `/v3/api-docs` 생성).

## 3. 변경 사항 — 프로덕션 코드 3곳

### 3.1 `V15__sermon_view_permission.sql` (신규) — V13 패턴 그대로

```sql
-- 설교 회원전용 전환: SERMON_VIEW 신설 (V13 패턴).
-- 관리자에겐 조회+쓰기 모두, MEMBER(승인 교인)에겐 조회만.
INSERT INTO permissions (name, description) VALUES
    ('SERMON_VIEW', '설교 조회(회원 전용 열람)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r
JOIN permissions p ON p.name = 'SERMON_VIEW'
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN', 'MEMBER');
```

- 기존 배포 DB는 V15 적용으로 자동 반영, 신규 DB는 V2 → V15 순서로 동일 결과.
- `USER`·익명 제외(갤러리와 동일 — 승인 전 차단이 목적).
- 인덱스 추가 없음 → `MigrationIndexTest` 변경 불필요.

### 3.2 `SecurityConfig` — 매처 규칙 1줄 추가

`anyRequest().permitAll()` **앞에** 추가:

```java
.requestMatchers("/api/sermons/**")
.hasAuthority("SERMON_VIEW")
```

- `/api/admin/sermons/**`는 위쪽 `/api/admin/**` 매처가 먼저 잡으므로 무영향(기존 `authenticated` + `@PreAuthorize` 유지).
- 에러 시맨틱은 기존 핸들러가 자동 적용: 비로그인·만료 = 401 `INVALID_TOKEN`(`JwtAuthenticationEntryPoint`),
  로그인+권한없음 = 403 `ACCESS_DENIED`(`JwtAccessDeniedHandler`). 갤러리와 동일 → 신규 코드 불필요.
- 클래스 Javadoc(경로 3분법 설명)의 "그 외 공개" 부분에 설교=회원전용 항목 반영.

### 3.3 `SermonController` — 문서 문구 갱신 (OpenAPI 신호)

- 클래스 Javadoc "설교 공개 조회 API … 비인증 — anyRequest permitAll" → 회원전용 문구로.
- 두 `@Operation` description의 "인증(JWT): 불필요" → **"인증(JWT): 필요 — `SERMON_VIEW` (회원 전용)"**.
- 목록/상세 summary에 "(회원전용)" 부기.
- 별도 OpenAPI 커스터마이저 변경 불필요(§2 참조 — 전역 bearer 요구가 이미 모든 오퍼레이션에 적용됨).

## 4. 변경 사항 — 테스트

신규 권한이 16번째로 추가되고 `MEMBER` 집합이 3개로 늘면서, 카운트/집합을 하드코딩한 단언과
설교 GET을 무인증으로 호출하던 테스트가 파손된다. 아래를 모두 반영한다.

### 4.1 시드/권한 카운트 단언 (5곳)

| 파일 | 위치 | 수정 |
|---|---|---|
| `RbacSeedIntegrityTest` | `seeds_fifteen_permissions()` | 리스트에 `SERMON_VIEW` 추가(총 16), 메서드명 `seeds_sixteen_permissions()`로 **개명** |
| `RbacSeedIntegrityTest` | `role_permission_matrix_matches_spec()` | `SUPER_ADMIN`/`ADMIN` `hasSize(15)`→`16`; `MEMBER` `containsExactlyInAnyOrder(...)`에 `SERMON_VIEW` 추가 |
| `PermissionApiTest` | 목록 길이 단언 | `jsonPath("$.length()").value(15)`→`16` + 인라인 주석에 V15 추가 |
| `RoleApiTest` | SUPER_ADMIN permissions 길이 | `jsonPath("$[0].permissions.length()").value(15)`→`16` + 주석 |
| `MeApiTest` | `me_returns_db_latest_with_roles_and_permissions()` | MEMBER `containsInAnyOrder`에 `SERMON_VIEW` 추가 — **§5.5 검수 커버** |

### 4.2 `SermonApiTest` — 회원전용 전환

현재 `/api/sermons`·`/api/sermons/{id}` GET을 **무인증** 호출하는 6개 테스트가 401로 파손된다:
`public_list_paginates_and_omits_content`, `public_list_filters_by_preacher`,
`public_detail_increments_view_count`, `detail_unknown_is_404`,
`delete_soft_deletes_then_detail_404`(끝의 상세 GET), `author_is_masked_when_member_withdrawn`.

- **`viewerToken()` 헬퍼 추가**: `SERMON_VIEW` 보유 토큰 발급. (기존 `adminToken()`은 `SERMON_WRITE` **단일** 권한이라
  GET에 못 씀 — 별도 뷰어 토큰 필요.)
- 위 6개 GET 호출에 `viewerToken()` 부착.
- **개명**: `public_list_*` → `members_list_*`, `public_detail_*` → `members_detail_*` (이제 공개 아님).
  `detail_unknown_is_404`는 뷰어 토큰 부착 후에도 404 유지(인증 통과 → 미존재).
- **신규 케이스 추가**:
  - `list_anonymous_is_401` — 무인증 `GET /api/sermons` → 401 `INVALID_TOKEN` (§5.1).
  - `list_without_sermon_view_is_403` — `SERMON_VIEW` 없는 토큰(예: `MEDIA_MANAGE`) → 403 `ACCESS_DENIED` (§5.2).
  - MEMBER/ADMIN 200·상세 viewCount 증가는 4.2의 전환 테스트가 커버(§5.3·5.4).

## 5. 검수 기준

1. 비로그인 `GET /api/sermons` → 401 `INVALID_TOKEN`.
2. `SERMON_VIEW` 미보유(승인 전 `USER` 등) → 403 `ACCESS_DENIED`.
3. `SERMON_VIEW` 보유 → 200, 목록·상세 정상. 상세 조회 시 viewCount 증가 유지.
4. `ADMIN`/`SUPER_ADMIN` → 200 (V15로 `SERMON_VIEW` 보유).
5. `GET /api/members/me`의 `permissions` 배열에 `SERMON_VIEW` 포함(MEMBER 이상).
6. 어드민 쓰기(등록/수정/삭제, `/api/admin/sermons/**`) 회귀 없음.
7. `./gradlew test` 전체 그린(4.1·4.2 반영 후).
8. OpenAPI 재생성 → 프론트 `docs/api-docs.json` 교체분 전달(§7 핸드오프).

## 6. 변경하지 않는 것

- 상세 GET의 조회수 +1 부수효과 — 유지.
- `/api/admin/sermons/**` 쓰기 권한(`SERMON_WRITE`) — 무변경.
- 응답 스키마(`SermonCardResponse`·`SermonDetailResponse`) — 무변경.
- 다른 공개 도메인(공지·일정·주보·부서·태그) — 무변경.
- **`GET /api/main`(공개 통합 피드) — 무변경, 의도된 결정.** `MainService`가 `sermonService.list()`를 인프로세스로 호출하므로 `/api/sermons/**` HTTP 매처가 적용되지 않아, 익명 사용자도 홈 피드에서 최신 설교 **카드 3건(제목·설교자·시리즈·날짜·조회수 등 메타만, 본문 `content` 제외)** 을 계속 본다. 이는 수용한다 — 실제 게이트는 프론트 홈 전환이 담당하고, 노출은 최신 3건 카드 메타로 한정되며(본문 없음), 클릭 시 상세(`/api/sermons/{id}`)는 회원전용 매처로 차단되기 때문. 완전 블랙아웃이 필요하면 별도 이슈로 `MainResponse`에서 설교 제거/조건화를 결정한다.
- `MigrationIndexTest`(인덱스 미추가), `OpenApiOperationCustomizerTest`(설교 400 주입만 확인, 유지).
- `CHANGELOG.*` / `version.yml` / `build.gradle` version — 자동화 소유, 손대지 않음.

## 7. 운영 주의사항 / 핸드오프

- **어드민 UI로 만든 커스텀 역할**에는 V15가 자동 부여하지 않음(역할명 기준 시드). 설교 열람이 필요한 커스텀 역할은
  역할·권한 매트릭스에서 `SERMON_VIEW`를 수동 부여해야 한다.
- `SERMON_WRITE`만 있고 `SERMON_VIEW`가 없는 역할은 프론트 수정 화면(상세 GET 시드)을 쓸 수 없다.
  기본 시드에서 관리자 역할은 둘 다 가지므로 문제 없음 — 커스텀 역할 구성 시에만 주의.
- 이 변경으로 설교 상세(`/api/sermons/{id}`)가 검색엔진·비로그인 공유 링크에서 사라진다(회원전용 전환의 의도된 결과). 단 홈 피드(`/api/main`)의 카드 프리뷰는 공개 유지(위 §6 참조).
- **클릭스루 엣지 케이스(백엔드 보장, 확인됨)**: `/api/main`에 노출된 설교 카드를 익명/미승인 사용자가 클릭 → 상세 `GET /api/sermons/{id}` 호출은 익명 = 401 `INVALID_TOKEN`(로그인 필요), 로그인했으나 `SERMON_VIEW` 미보유(`USER`) = 403 `ACCESS_DENIED`로 차단된다. 프론트는 401→로그인, 403→교인 승인 안내로 라우팅(라우팅 자체는 프론트 책임). 백엔드 차단은 `SermonApiTest.detail_anonymous_is_401`·`detail_without_sermon_view_is_403`로 검증.
- **OpenAPI 핸드오프**: 백엔드 실행 후 `/v3/api-docs` 재수집 → 산출물을 church-frontend에 전달 → 프론트가
  `docs/api-docs.json` 교체. 리포에 커밋 산출물 없음이므로 백엔드 PR 범위는 코드·문구·테스트까지.

## 8. 구현 순서 (TDD)

1. 4.1·4.2의 신규/전환/개명 테스트를 먼저 반영(RED — 프로덕션 코드 없이 실패 확인).
2. 3.1 V15 마이그레이션 → 3.2 SecurityConfig 매처 → 3.3 컨트롤러 문구 반영(GREEN).
3. `./gradlew test` 전체 그린 확인.
