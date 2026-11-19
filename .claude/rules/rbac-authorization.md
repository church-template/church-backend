# RBAC & Authorization

Discord-style dynamic RBAC. See `docs/church-backend-spec.md` §3–§4 for the full model. These are the invariants that must hold in every authorization path.

## Two independent axes — never couple them

- **Position (직분)** and **Role/Permission** are separate. A high position grants **zero** authority. Never derive permissions from `position_id`.
- Authorization decisions are made **per Permission**, never per Role. Roles are just bundles.

```java
@PreAuthorize("hasAuthority('SERMON_WRITE')")   // correct — check the permission
// NOT hasRole('ADMIN'), NOT "if position == 목사"
```

- DB stores permission/role names **without** any `ROLE_`/authority prefix. Apply the prefix only at the Spring Security mapping layer.

## Priority hierarchy guard (escalation prevention)

Three priority rules, **split by operation**:

- **Grant/revoke a role to a member** — target `priority` must be **strictly below** the requester's `maxPriority` (same level **blocked**). Only a strictly-higher role delegates/strips a lower one; a peer cannot. The top role (`SUPER_ADMIN`) has nothing above it → **never grantable via API; seed/DB only**. Equal-or-higher → `403`. (Validator: `validateGrantable`, used by `MemberRoleService`.)
- **Modify the role itself** — `PATCH /roles/{id}`, `DELETE /roles/{id}`, `PUT /roles/{id}/permissions` — target `priority` must be **at or below** the requester's `maxPriority` (same level allowed). Strictly higher → `403`. (Validator: `validateAssignable` via `validateMutable`.)

- **Set a role's priority** — `POST /roles` and a `PATCH /roles/{id}` that changes `priority` — the new `priority` must be **strictly below** the requester's `maxPriority` (same level **blocked**). Otherwise the grant rule is bypassable: grant a low role, then raise it to your own level to mint a peer admin. Re-sending the unchanged value is not a change. Equal-or-higher → `403`. (Validator: `validateNewPriority`, used by `RoleService`.)
- **Admin action on another member** — `PATCH /api/admin/members/{uuid}`, `PUT …/position`, `POST …/reset-password` — the target member's top `priority` must be **at or below** the requester's `maxPriority` (same level allowed). Strictly higher → `403`. `MEMBER_MANAGE` alone is not enough: an `ADMIN` must not reset a `SUPER_ADMIN`'s password or change its phone. Self-service (`/api/members/me`) is not subject to this. (Validator: `validateAssignable`.)

Additional hard stops:

- `is_system = true` roles: reject modify/delete outright, regardless of priority.
- **Self-protection**: a requester cannot grant/revoke their **own** roles.
- **Last SUPER_ADMIN**: if only one SUPER_ADMIN exists **in that church**, reject its revoke/demote/delete.

Roles and positions are **per church** (`(church_id, name)` unique): each church has its own `SUPER_ADMIN`/`ADMIN`/`MEMBER`/`USER` rows, and every count/lookup is scoped to the request's church. `permissions` is the one global catalog. The church owner is that church's `SUPER_ADMIN`, created by `scripts/new-church.sh`; the operator keeps no standing app account — see [[multi-church-template]].

## JWT contents

- `sub` = member **`uuid`** (never the BIGINT `id`).
- Carry **flattened `permissions`** + `maxPriority` — not roles.
- Access tokens carry **`cid`** (church id). A token whose `cid` differs from the request's church — or has no `cid` — is treated as invalid (`401 INVALID_TOKEN`). Refresh tokens have no `cid`; a refresh at another church fails because the member lookup is tenant-filtered.
- One `JWT_SECRET` for the whole platform (not per church).
- Token reflects issue-time values; role/permission changes apply on next refresh. For live authority decisions on sensitive screens, read DB (`GET /api/members/me`), not the token.

## Path authorization — SecurityConfig 매처 체인이 정본

아래 표는 교회 접두사 `/church/{slug}`를 **뗀 뒤의** 경로다. `TenantFilter`가 보안 필터 체인보다 앞에서 접두사를 떼므로 매처는 접두사를 모르고, `/church/{slug}/api/admin/**`도 2번 규칙으로 보호된다. 접두사 뒤가 `/api/`가 아니면(`/church/x/actuator/…`) 404.

매처는 **선언 순서대로 선순위 매칭**된다 (`global/config/SecurityConfig.securityFilterChain`):

| 순서 | Path | Rule |
|---|---|---|
| 1 | swagger(`/v3/api-docs`, `/v3/api-docs/**`, `/docs/swagger-ui/**`, `/docs/swagger-ui.html`) · `/error` · `/actuator/health` | permitAll |
| 2 | `/api/admin/**` | 인증 필수 + 세부 권한은 메서드 `@PreAuthorize` (예: `SERMON_WRITE`) |
| 3 | `/api/bible-challenges/**` | `CHALLENGE_PARTICIPATE` (승인 교인 전용) |
| 4 | `/api/vehicle-runs/**` | `VEHICLE_APPLY` (승인 교인 전용) |
| 5 | 나머지 `/api/**` (공지·일정·주보·설교·갤러리·부서·문의·메인·공개 범위 등) | 매처는 public. 단 공지·일정·주보·설교·갤러리 읽기는 아래 컨트롤러 가드가 판단 |

### 5개 메뉴 — 교회 설정에 따라 컨트롤러 가드가 판단 (#95)

공지(`/api/notices/**`)·일정(`/api/events/**`)·주보(`/api/bulletins/**`)·설교(`/api/sermons/**`)·갤러리(`/api/gallery/**`) 읽기는 교회마다 DB(`content_visibility`)에 둔 공개 범위를 따른다. 설정이 교회별이라 고정 경로 매처로 표현할 수 없어, 매처는 public으로 두고 각 컨트롤러가 `ContentAccessGuard`(`domain/visibility` — `global`은 `domain`에 의존할 수 없다)를 부른다. 이전의 `/api/gallery/**`·`/api/sermons/**` 고정 매처는 없앴다.

| 메뉴 설정 | 글 `membersOnly`(설교·앨범만) | 조회 권한 없음 | 조회 권한 있음 |
|---|---|---|---|
| `MEMBERS` | (무관) | 목록·상세 401 `INVALID_TOKEN`(비로그인) / 403 `ACCESS_DENIED`(로그인, 권한 없음) | 전부 |
| `PUBLIC` | false | 봄 | 봄 |
| `PUBLIC` | true | 목록에서 빠짐, 상세 401/403 | 봄 |

- 조회 권한 = `NOTICE_VIEW`·`EVENT_VIEW`·`BULLETIN_VIEW`·`SERMON_VIEW`·`GALLERY_VIEW`. 설정 권한 = `VISIBILITY_MANAGE`(`GET /api/visibility` 공개, `PUT /api/admin/visibility`).
- **무효 토큰**: 이 5개 경로에 `Authorization` 토큰이 왔는데 무효·만료면 메뉴가 `PUBLIC`이어도 401 `INVALID_TOKEN`(`JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE`로 표시하고 가드가 읽는다). 토큰 없음·빈 `Bearer `는 비로그인으로 통과.
- **"누가 보는가"는 컨트롤러에서만 판단한다.** 서비스는 `includeMembersOnly` 같은 명시적 인자만 받는다 — 서비스가 로그인 상태를 읽으면 `/api/main` 캐시(키 = 교회 id)에 교인용 결과가 섞인다.
- 설교·앨범 목록의 `?membersOnly=` 필터는 쓰기 권한(`SERMON_WRITE`·`GALLERY_WRITE`) 보유자에게만 적용, 그 외 무시(교인 전용 글 수 노출 방지). 글 단위 검사는 조회수 증가보다 먼저.
- 기본값: 기존 교회 = 설교·갤러리 `MEMBERS`, 나머지 `PUBLIC`(V21). 새 교회·행 누락 = 갤러리만 `MEMBERS`(`ContentDomain.defaultLevel`, `scripts/new-church.sql`).
- 부서에 연결된 앨범은 `membersOnly`가 될 수 없고 `membersOnly` 앨범은 부서에 연결할 수 없다(400 `INVALID_INPUT_VALUE`). **알려진 한계**: 두 검사는 잠금 없이 각자 검증만 하므로 두 요청이 동시에 오면 둘 다 통과할 수 있다(잠금은 두지 않는다 — 스펙 §4.5).

### 그 밖

- `GET /api/media/{id}`는 JWT가 필요 없지만 **지금 누구나 볼 수 있는 글에 쓰인 미디어만** 익명에게 열린다(위 설정·`membersOnly`를 따른다, 부서는 항상 공개). 그 외는 서명 주소(`?exp=&sig=`)가 있어야 하고, 없으면 `MEDIA_ACCESS_ENFORCED=true`일 때 404다. 경로 매처가 아니라 `MediaService.serve`가 판단한다 — [[media-library]].
- `/api/main`(통합 조회)은 **의도적으로 public 유지**하되 방문자 기준으로 고른다: `membersOnly` 설교 제외, 메뉴가 `MEMBERS`인 공지·일정 제외, 메뉴가 `MEMBERS`인 설교는 카드만(상세는 막힘 — #53 결정 유지).
- `MEMBER` role = 승인 교인 — `*_VIEW` 5개·`CHALLENGE_PARTICIPATE`·`VEHICLE_APPLY` 보유(V2·V13·V15·V16·V21 시드; V21은 `GALLERY_VIEW`를 가진 모든 역할에 새 조회 권한 3개를 줬다). 가입 직후 기본 `USER`와 익명은 교인 전용 메뉴·경로에서 차단. `MEMBER` 부여가 곧 교인 승인 절차다(이메일 인증 대체).
- 새 회원전용 경로를 추가할 땐 `anyRequest().permitAll()` **앞에** 매처를 넣고, 이 표와 CLAUDE.md·AGENTS.md 요약도 함께 갱신할 것. 교회별로 열고 닫아야 하는 메뉴라면 매처 대신 `ContentDomain`에 더하고 `ContentAccessGuard`를 쓴다.

## Identifier language

Code-facing keys are **English** (`SERMON_WRITE`, `ADMIN`, `MEMBER`); user-facing labels are **Korean** (`roles.description`, position/tag names). Check English `name` in code, show Korean `description` to humans.
