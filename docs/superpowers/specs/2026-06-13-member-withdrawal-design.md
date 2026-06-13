# 회원탈퇴(자가탈퇴) 설계

- 이슈: #38 회원탈퇴 기능 추가
- 브랜치: `20260613_#38_회원탈퇴_기능_추가`
- 스펙 근거: `docs/church-backend-spec.md` §5.2 (회원), 작성자 표시 정책(§5), `persistence-conventions`(soft delete), `rbac-authorization`(마지막 SUPER_ADMIN 보호)

## 배경 / 문제

회원탈퇴를 실행하는 경로가 없다. 시스템은 "탈퇴한 회원"의 존재를 전제로 설계돼 있으나(읽기측 스캐폴딩만 존재) 탈퇴시키는 동작이 빠져 있다.

- 있는 것: `members.deleted_at`, 전화번호 부분 유니크(`WHERE deleted_at IS NULL`), `AuthorDisplayService`의 `(탈퇴한 사용자)` 마스킹, 모든 회원 조회의 `deleted_at IS NULL` 필터, `Member.softDelete()`(미배선).
- 없는 것: 탈퇴 엔드포인트·서비스 로직. 스펙 §5.2에도 미정의.

## 범위

- 본인 자가탈퇴만. (관리자 강제탈퇴는 범위 밖.)
- soft delete + 개인정보(PII) 스크럽. 물리 삭제 아님(콘텐츠 `created_by`/`updated_by`·`media.uploaded_by`가 RESTRICT FK라 물리삭제는 막히고, 마스킹 설계와도 충돌).

## 결정 사항 (확정)

- 탈퇴 주체: 본인만.
- PII 처리: soft delete + 스크럽(전화번호·이름·이메일·비밀번호).
- 재가입: 탈퇴 전화번호로 재가입 허용 = 새 계정 생성(새 uuid/id, USER부터). 복구 아님(영구 탈퇴).
- 작성자 표시: 기존 정책 그대로 — 탈퇴자 작성글은 `(탈퇴한 사용자)`. 마스킹은 `deletedAt` 기준이라 이름 스크럽과 무관하게 동작.
- 세션: 전체 무효화 — `RefreshTokenStore.revokeAll(uuid)` + 현재 access 토큰 블랙리스트.
- 재인증: 현재 비밀번호 재확인 요구(불일치 401).
- 마지막 활성 SUPER_ADMIN 자가탈퇴: 차단(403).

## 엔드포인트

- `DELETE /api/members/me` — `MeController`(클래스 레벨 `@PreAuthorize("isAuthenticated()")` 상속).
- 요청 본문: `WithdrawRequest { @NotBlank String password }`.
- access 토큰: `Authorization: Bearer` 헤더에서 추출(logout과 동일 패턴).
- 응답: `204 No Content`.
- 위임: `memberService.withdraw(principal.id(), principal.uuid(), accessToken, request.password())`.

## 스키마

마이그레이션 없음. 기존 컬럼(`deleted_at`, `phone`, `name`, `email`, `password`)만 사용.

## 서비스 흐름 — `MemberService.withdraw(memberId, uuid, accessToken, password)`

1. `findActive(memberId)` — 활성 회원 로드(이미 탈퇴/미존재면 404 `RESOURCE_NOT_FOUND`).
2. 재인증: `passwordEncoder.matches(password, member.getPassword())` 실패 → 401 `AUTHENTICATION_FAILED`.
3. 마지막 SUPER_ADMIN 가드: `member.hasRole(SUPER_ADMIN)`이면 `countByRoles_NameAndDeletedAtIsNull(SUPER_ADMIN)` 조회 후 `RoleHierarchyValidator.validateNotLastSuperAdmin(true, count)` → 마지막이면 403 `ACCESS_DENIED`.
4. `member.withdraw()` — soft delete + PII 스크럽.
5. `persist(member)`(flush).
6. 전체 세션 무효화: `refreshTokenStore.revokeAll(uuid)` + `accessTokenBlacklister.blacklist(accessToken)`.

의존성 추가(모두 `global` → `domain→global` 단방향 준수): `RefreshTokenStore`, `AccessTokenBlacklister`(신규), `RoleHierarchyValidator`. `PasswordEncoder`는 기보유.

Redis 무효화는 best-effort(logout과 동일). DB 커밋이 성공의 기준이며 Redis 장애 시 토큰 무효화는 누락될 수 있다(허용 위험).

## 도메인 메서드 — `Member.withdraw()`

```
softDelete();              // deletedAt = now (기존 메서드 배선)
this.phone    = "(탈퇴)";          // 비식별 토큰값. soft-deleted라 활성 부분유니크서 제외 → 재가입 자유, 값 중복 무관
this.name     = "(탈퇴한 사용자)";  // 표시는 deletedAt 기준 마스킹이라 무관, 저장값도 비식별로
this.email    = null;
this.password = "(withdrawn)";     // 비-BCrypt 상수 센티넬 → matches()가 항상 false라 자격증명 무력화.
                                   //   인코더 의존 불필요(엔티티 순수성 유지). 로그인 조회는 deleted 제외라 실제 호출도 안 됨.
```

유지: `position_id`, `agreed_at`, `terms/privacy` 플래그, `member_roles`(PII 아님 + `deleted_at` 필터로 조회·SUPER_ADMIN 카운트서 자동 제외), `id`/`uuid`(불변).

## 토큰 헬퍼 추출 (타깃 리팩터링)

`AuthService.blacklistAccess`(JWT 파싱 → jti 블랙리스트)가 현재 private. 탈퇴와의 중복을 피하려고 `global/security`에 `AccessTokenBlacklister.blacklist(String rawAccessToken)`로 추출하고, `AuthService.logout`도 이를 사용하도록 교체(중복 제거, 변경 범위 최소). `RefreshTokenStore.revokeAll(uuid)`는 이미 public이라 직접 재사용.

## 에러 매핑

| 상황 | 코드 |
|---|---|
| 비밀번호 누락(검증 실패) | 400 `INVALID_INPUT_VALUE` |
| 비밀번호 불일치 | 401 `AUTHENTICATION_FAILED` |
| 마지막 활성 SUPER_ADMIN | 403 `ACCESS_DENIED` |
| (방어) 비활성/미존재 회원 | 404 `RESOURCE_NOT_FOUND` |

## 테스트

- Service 단위: 성공(soft-delete+스크럽 적용, `revokeAll`·blacklist 호출 검증), 비번 불일치→401, 마지막 SUPER_ADMIN→403.
- API 통합: `DELETE /me` 정상 204 → 동일 토큰 재요청 401(블랙리스트), 잘못된 비번 401, 비번 누락 400.
- Repository: 탈퇴 후 `existsByPhoneAndDeletedAtIsNull(원번호)` = false → 동일 번호 재가입 가능.
- AuthorDisplay: 탈퇴자 작성글 `(탈퇴한 사용자)` 표시(deletedAt 기준).
- 회귀: 헬퍼 추출 후 `logout` 기존 동작 유지.

## 스펙 문서 반영

`docs/church-backend-spec.md` §5.2 표에 `DELETE /api/members/me`(인증) 행 추가 + 탈퇴 정책 주석(soft-delete+PII 스크럽 · 재인증 · 마지막 SUPER_ADMIN 차단 · 전체 세션 무효화 · 재가입=새 계정).

## 비범위 (YAGNI)

- 관리자 강제탈퇴, 탈퇴 계정 복구/재활성화, 탈퇴 유예기간, 탈퇴 사유 수집, 미동의 회원 백엔드 전면 차단 인터셉터.
