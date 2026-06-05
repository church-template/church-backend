# D4 인증(Auth) 도메인 설계 (#9)

- 작성일: 2026-06-05
- 이슈: #9 인증 도메인 (signup·login·refresh·logout)
- 선행: G3(보안·JWT 인프라, #4 완료)·D3(member, #8 완료) — Phase 2 마지막
- 스펙 근거: `docs/church-backend-spec.md` §4.1~4.3, §5(공통 규칙)·§5.1, §9 / 규칙: `.claude/rules/{rbac-authorization,api-conventions,multi-church-template}.md`

---

## 1. 목표와 범위

회원가입·로그인·토큰 재발급·로그아웃 4개 엔드포인트를 구현해 인증 흐름을 완성한다. **순수 배선 작업** — 토큰 발급/검증·Redis 저장소·`Member.create()`·권한 플래트닝·phone 정규화는 G3·D3가 이미 만들어 두었고, D4는 이를 `/api/auth/*`로 엮을 뿐이다.

### 포함 (D4)
- `POST /api/auth/signup` — phone·name·password 필수·email 선택·약관 2종 필수·`USER` 자동 부여·uuid 발급. **201 + 회원 요약**(자동 로그인 안 함).
- `POST /api/auth/login` — phone+password, 실패 동일 401, Access 발급·Refresh Redis 저장, `requiresAgreement` 플래그.
- `POST /api/auth/refresh` — Redis refresh 확인 후 **Access만 재발급**(refresh 회전 없음, 기존 refresh echo).
- `POST /api/auth/logout` — **현재 기기만**: access jti 블랙리스트 + 해당 기기 refresh revoke. 인증 필요(메서드 보안).

### 제외 (범위 밖)
- 전체 기기 로그아웃·인가 즉시 반영(Redis 강제 만료 `revokeAll`)은 선택 기능 — 범위 외(`RefreshTokenStore.revokeAll` 계약은 G3에 존재하나 D4는 호출 안 함).
- 약관 미동의 회원 백엔드 전면 차단 인터셉터 — 스펙상 선택, 프론트 안내(`requiresAgreement`)까지만.
- SecurityConfig·ErrorCode·마이그레이션 — **무수정**. 단 `domain/member`의 `PhoneNumbers`·`MemberRepository`에 소폭 수정 있음(§6).

---

## 2. 재사용하는 기존 인프라 (신규 작성 없음)

| 컴포넌트 | 위치 | D4가 호출하는 부분 |
|---|---|---|
| `JwtTokenProvider` | `global/security` | `issueAccess(principal, position, permissions)`·`issueRefresh(uuid)`·`parse(token)` |
| `RefreshTokenStore` | `global/security/redis` | `save(uuid,jti,expiresAt)`(login)·`isValid(uuid,jti)`(refresh)·`revoke(uuid,jti)`(logout) |
| `TokenBlacklist` | `global/security/redis` | `blacklist(jti,expiresAt)`(logout) |
| `MemberPrincipal` | `global/security` | login·refresh에서 토큰 발급용으로 구성 |
| `Member.create(...)` | `domain/member` | signup(약관 invariant·uuid 생성 재사용) |
| `MemberAuthorities` | `domain/member` | `permissions(member)`·`maxPriority(member)` — 토큰 클레임 |
| `MemberRepository` | `domain/member` | `findByPhoneAndDeletedAtIsNull`(login, **@EntityGraph 추가 — §6**)·`findByUuidAndDeletedAtIsNull`(refresh)·`existsByPhoneAndDeletedAtIsNull`(signup) |
| `RoleRepository.findByName` | `domain/role` | signup `USER` 역할 grant |
| `PhoneNumbers.normalize` | `domain/member` | signup·login phone 정규화 (**public 승격 — §6**) |
| `PasswordEncoder`(BCrypt) | `global/config` | signup encode·login matches |

- JWT 클레임은 G3 `issueAccess`가 이미 스펙 §4.2대로(`sub`=uuid·`name`·`position`·펼친 `permissions`·`maxPriority` + 내부용 `mid`·`type`·`jti`) 채운다 — D4는 인자만 넘긴다.

---

## 3. 엔드포인트 명세 & 데이터 흐름

### 3.1 `POST /api/auth/signup` (공개) → `201 Created`
요청 `SignupRequest`:
```json
{ "phone": "010-1234-5678", "name": "홍길동", "password": "...", "email": "선택", "termsAgreed": true, "privacyAgreed": true }
```
- 검증(Bean Validation): `phone`·`name` `@NotBlank`, `password` `@NotBlank @Size(min=8)`(복잡도 강제 없음 — `multi-church-template` 룰), `email` 선택(`@Email`, null 허용), `termsAgreed`·`privacyAgreed` `@AssertTrue`(누락/false → `400 INVALID_INPUT_VALUE`).
- 흐름:
  1. `PhoneNumbers.normalize(phone)` → 숫자만.
  2. `existsByPhoneAndDeletedAtIsNull` 이면 `409 DUPLICATE_RESOURCE`(가입 여부 노출이지만 가입 단계라 무방).
  3. `Member.create(normPhone, name, encoder.encode(password), email, null, termsAgreed, privacyAgreed)` — position=null. create invariant가 약관 2종 재검(DTO와 이중 방어).
  4. `member.grantRole(roleRepository.findByName("USER").orElseThrow(IllegalState "USER 시드(V2) 없음"))`.
  5. `saveAndFlush` — 동시 가입 경합은 partial unique 위반을 `DataIntegrityViolationException` try/catch로 `409 DUPLICATE_RESOURCE` 백스톱(D3 패턴 재사용).
- 응답 `SignupResponse { uuid, name, phone(정규화값), roles:["USER"] }`. 토큰 없음 → 클라이언트가 이어서 login 호출.

### 3.2 `POST /api/auth/login` (공개) → `200 OK`
요청 `LoginRequest { phone, password }`(둘 다 `@NotBlank`).
- 흐름:
  1. `normalize(phone)` → `findByPhoneAndDeletedAtIsNull` → 없으면 `AUTHENTICATION_FAILED`.
  2. `encoder.matches(raw, member.password)` false → `AUTHENTICATION_FAILED`.
     - **두 분기 모두 동일 예외**(스펙 §4.1 가입 여부 노출 방지). 동일 응답이면 충분(상수시간까지는 요구 안 함).
  3. `principal = MemberPrincipal(id, uuid.toString(), name, MemberAuthorities.maxPriority(member))`, `position = position?.name`, `permissions = MemberAuthorities.permissions(member)`.
  4. `access = issueAccess(principal, position, permissions)`, `refresh = issueRefresh(uuid)`.
  5. 발급한 refresh를 `parse`해 `jti`·`exp` 추출 → `refreshTokenStore.save(uuid, jti, exp)` (다중 세션 등록).
  6. `requiresAgreement = !(member.isTermsAgreed() && member.isPrivacyAgreed())`.
- **트랜잭션·페치**: `AuthService`는 `@Transactional(readOnly=true)` 기본, `signup`만 `@Transactional`. login은 `findByPhoneAndDeletedAtIsNull`에 `@EntityGraph({"position","roles","roles.permissions"})`를 추가(형제 메서드와 동일)해 roles·permissions를 한 쿼리로 페치 → 플래트닝 시 lazy 로딩 예외·N+1 방지(§6).
- 응답(스펙 §5.1):
```json
{ "tokens": { "accessToken": "...", "refreshToken": "..." },
  "member": { "uuid": "...", "name": "홍길동", "phone": "01012345678", "position": "장로|null", "roles": ["USER"] },
  "requiresAgreement": false }
```
- `member.phone`은 **정규화값(하이픈 없음, 예 `01012345678`)** — 표시용 하이픈은 프론트 책임(사용자 확정). **계약 권위값 = 숫자만**(스펙 §3.2 저장 규칙·D3 `MeResponse`와 일치). 스펙 §5.1 예시의 `010-1234-5678`은 입력 문자열을 보여준 **예시일 뿐**이며 응답 계약이 아니다. 문서 충돌 제거를 위해 상위 스펙 예시도 정규화값으로 정정 권장(별도 결정 사항).

### 3.3 `POST /api/auth/refresh` (공개) → `200 OK` — **Access만 재발급**
요청 `RefreshRequest { refreshToken }`(`@NotBlank`).
- 흐름:
  1. `parse(refreshToken)`을 `try/catch(JwtException | IllegalArgumentException)`로 감싸 실패(만료·위변조·형식) 시 `BusinessException(ErrorCode.INVALID_TOKEN)`(401)로 변환. **GlobalExceptionHandler에 JwtException 전용 핸들러가 없어 catch 안 하면 `handleUnexpected` fallback으로 500이 된다.**
  2. `type != "refresh"`(access를 refresh 자리에 사용) → `401 INVALID_TOKEN`.
  3. `uuid = sub`, `jti = id`. `refreshTokenStore.isValid(uuid, jti)` false(회수·만료·미등록) → `401 INVALID_TOKEN`.
  4. `findByUuidAndDeletedAtIsNull(uuid)` 없음(탈퇴 회원) → `401 INVALID_TOKEN`.
  5. **DB에서 권한 재조회**(스펙 §4.1 "변경은 다음 갱신 시 반영") → principal·position·permissions 재구성 → `issueAccess`.
- 응답 `{ "tokens": { "accessToken": "<새 access>", "refreshToken": "<입력 그대로 echo>" } }`. refresh는 불변·Redis 재저장 없음(여전히 유효). 응답을 login의 `tokens` 객체 형태와 통일하기 위해 기존 refresh를 echo.

### 3.4 `POST /api/auth/logout` (인증) → `204 No Content` — **현재 기기만**
요청 `LogoutRequest { refreshToken }`(`@NotBlank`). 컨트롤러 `@PreAuthorize("isAuthenticated()")`.
- 흐름(`@AuthenticationPrincipal MemberPrincipal` + `@RequestHeader(AUTHORIZATION)`):
  1. 헤더의 access 토큰을 `Bearer ` 제거 후 `parse` → `jti`·`exp` → `tokenBlacklist.blacklist(jti, exp)`. (필터가 이미 검증했으므로 재파싱은 성공 보장. 방어적 try/catch로 실패 시 skip.)
     - `MemberPrincipal`에 jti가 없어 **헤더 토큰을 재파싱**한다(G3 필터·principal 무수정 — 가장 작은 blast radius).
  2. body `refreshToken`을 `try/catch(JwtException | IllegalArgumentException)`로 `parse` → `type=="refresh"` **이고** `sub == principal.uuid`일 때만 `refreshTokenStore.revoke(uuid, refreshJti)`. 무효(파싱 실패)/타인 토큰은 **skip**(여기선 INVALID_TOKEN을 던지지 않는다 — access 블랙리스트는 이미 완료, 멱등 로그아웃·정보 노출·타 세션 침해 방지).
- refresh revoke를 함께 하는 이유: access 블랙리스트(잔여 ≤1h)만으로는 14일 refresh로 재로그인돼 로그아웃이 실효성을 잃는다. (스펙 §4.1은 "블랙리스트"만 언급하나 G3 설계 L110이 이 매핑을 적시.)

---

## 4. 패키지·컴포넌트 (`domain/auth`, 작은 파일 다수)

| 컴포넌트 | 책임 | 인가 |
|---|---|---|
| `AuthController` | `/api/auth` signup·login·refresh·logout | logout 메서드만 `@PreAuthorize("isAuthenticated()")`, 나머지 공개 |
| `AuthService` | 4개 동작 오케스트레이션(~180줄, 단일 서비스) | — |
| dto `SignupRequest`/`SignupResponse` | 가입 입출력 | — |
| dto `LoginRequest`/`LoginResponse` | 로그인 입출력 | — |
| dto `RefreshRequest`/`RefreshResponse` | 재발급 입출력 | — |
| dto `LogoutRequest` | 로그아웃 입력(refreshToken) | — |
| dto `TokenPair`/`MemberSummary` | `tokens` 객체·**login 전용** 회원 요약 | — |

- **구조 선택**: signup/login/refresh/logout이 `MemberRepository`+`JwtTokenProvider`를 공유하므로 단일 `AuthService`(고응집). 분리(Registration/Token)도 가능하나 단일이 더 단순 — 커지면 분리(스펙 §7 "초기엔 통합 후 분리 무방"과 동일 정신).
- **DTO 결정(리뷰 Minor 반영)**: `MemberSummary{uuid,name,phone,position,roles}`는 **login 응답 전용**. `SignupResponse{uuid,name,phone,roles}`는 **별도 레코드(position 없음)** — 가입 시 position은 항상 null이라 의미 없는 필드를 응답에 넣지 않는다. `tokens`는 login/refresh 공유 `TokenPair{accessToken,refreshToken}`.
- **의존 방향**: `domain/auth → domain/member`(repo·`Member.create`·`MemberAuthorities`·`PhoneNumbers`)·`domain/role`(`RoleRepository`)·`global/security`. domain→domain·domain→global 모두 ArchUnit 허용(global→domain 금지에 저촉 안 함).
- **`MemberPrincipal.from(member)`를 만들지 않는다**: `MemberPrincipal`은 `global/security`에 있고 `Member`는 domain이라 global→domain 의존이 생긴다 → 구성은 `AuthService`(domain) 안에서 직접 한다.

---

## 5. 보안 결정

- **열거(enumeration) 방지**: 미존재·비번불일치 모두 단일 `AUTHENTICATION_FAILED`(401) 경로. 응답 동일이면 스펙 충족.
- **refresh 시 DB 권한 재조회**: refresh 토큰은 최소 클레임(sub·jti·type·exp)만 담으므로 권한은 DB에서 다시 읽어 새 access에 반영(스펙 §4.1·G3 설계 L95).
- **블랙리스트는 access 전용**: refresh 무효화는 `revoke`(키 삭제)로(G3 계약 L109).
- **logout access jti 확보**: principal에 jti가 없어 헤더 토큰 재파싱 — G3 필터/`MemberPrincipal` 무수정(blast radius 최소).
- **다중 세션 일관**: logout은 현재 기기 refresh만 revoke, 타 기기 세션 보존(고령 사용자 "예기치 못한 로그아웃" 방지 — G3 다중세션 취지).
- **PII/시크릿 로깅 금지**: phone·token·password를 로그·예외 메시지에 남기지 않는다(D3 부트스트랩 PII 정책과 동일).

---

## 6. 기존 코드 수정 (최소 침습 — `domain/member` 2건)

리뷰 Critical/Major 반영. auth는 별도 패키지(스펙 §7)라 member의 package-private 자원에 접근하려면 가시성 보정이 필요하다.

1. **`PhoneNumbers` → public 승격**: `final class PhoneNumbers`·`static String normalize`가 현재 **package-private**이라 `domain.auth`에서 호출하면 컴파일 실패. 클래스와 `normalize`를 `public`으로 변경(이미 주석에 "D4 로그인도 재사용" 의도 명시 — 누락된 가시성만 보정). 이동보다 제자리 public화가 최소 변경(`PhoneNumbersTest`는 같은 패키지라 영향 없음).
2. **`MemberRepository.findByPhoneAndDeletedAtIsNull`에 `@EntityGraph({"position","roles","roles.permissions"})` 추가**: 형제 메서드(`findById…`/`findByUuid…`)는 이미 보유. login이 roles·permissions를 펼치므로 한 쿼리로 페치해 lazy 로딩 예외·N+1 방지. 더불어 `AuthService`는 `@Transactional(readOnly=true)` 기본, `signup`만 `@Transactional`로 override.

무수정 유지:
- **`SecurityConfig`**: `/api/auth/**`는 `anyRequest().permitAll()`에 자연 포함(signup·login·refresh 공개). logout은 메서드 보안으로 자체 방어 → 익명 요청은 기존 핸들러가 401(`INVALID_TOKEN`)로 분기(G3·D3 `/api/members/me`와 동일).
- **`ErrorCode`**: `INVALID_INPUT_VALUE`·`AUTHENTICATION_FAILED`·`INVALID_TOKEN`·`DUPLICATE_RESOURCE` 전부 존재.
- **마이그레이션**: `members`·`member_roles`·`roles`(USER 시드) 전부 V2/V3 존재.
- **`JwtTokenProvider`**: login에서 발급 직후 refresh를 재파싱해 jti·exp 획득(provider가 반환 안 함). 자기 서명 토큰 재파싱은 항상 성공·저렴 → G3 변경보다 surgical.

**예외 변환 주의(신규 `AuthService` 코드)**: `JwtTokenProvider.parse`는 `JwtException`/`IllegalArgumentException`을 던지고 `GlobalExceptionHandler`에 전용 핸들러가 없다(확인: `handleUnexpected`만 받아 500). 따라서 refresh·logout의 모든 parse를 `try/catch`로 감싸 refresh는 `BusinessException(INVALID_TOKEN)`으로 변환, logout은 skip 처리(§3.3·§3.4).

---

## 7. 테스트 전략 (커버리지 80%+, TDD)

기존 하네스 재사용: `@SpringBootTest` + `@AutoConfigureMockMvc`(SB4 재배치 패키지) + `@Import(TestcontainersConfiguration)`. 인가 토큰이 필요한 검증은 실제 `login`으로 발급(D4는 login API가 생기므로 D3와 달리 `issueAccess` 직접 발급 불필요한 시나리오 다수). `@AfterEach`로 테스트 생성 회원·Redis 키 정리.

- **단위(`AuthServiceTest`, 리포·Redis mock)**: 로그인 미존재·불일치가 **동일 예외**, signup이 `USER` grant, refresh가 권한을 DB에서 재조회.
- **API 통합(`AuthApiTest`, Testcontainers)**:
  - signup: 성공(201·`USER` 부여·uuid 발급)·중복 전화(409)·약관 미동의(400)·짧은 비번(400)·필드 누락(400).
  - login: 성공(200·tokens·member·`requiresAgreement=false`)·오비번(401)·미존재(401 **동일 바디**)·약관 리셋 후 `requiresAgreement=true`.
  - refresh: 성공(새 access·refresh 동일)·**login↔refresh 사이 역할 부여 → 새 access에 권한 반영**·무효/만료/회수 refresh(401)·access를 body에 넣음(401 type 오류)·탈퇴 회원(401).
  - logout: 성공(204 → 직후 그 access로 보호경로 401·refresh로 재발급 차단)·무인증(401)·타인 refreshToken은 그 세션 미회수(skip 확인).
- **보안 회귀**: logout 후 블랙리스트된 access가 `/api/members/me`에서 401(G3 `SecurityBlacklistE2eTest` 패턴 확장).

---

## 8. 미해결/후속 메모

- 전체 로그아웃(`revokeAll`)·인가 즉시 반영은 선택 기능 — 후속 이슈로.
- 향후 httpOnly 쿠키 기반 refresh 전달로 전환 시 DTO·컨트롤러만 교체(서비스 토큰 로직 불변).
- `/api/main`·설교 목록 Redis 캐싱(스펙 §9)은 별개 작업 — auth와 무관.
