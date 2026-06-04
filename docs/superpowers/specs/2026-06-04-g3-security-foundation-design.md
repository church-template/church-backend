# G3 · Global 보안 기반 · JWT 인증·인가 설계

> 작성일: 2026-06-04
> 대상 이슈: `.issues/20260604_기능추가_G3_보안기반_JWT_인가_위계검증.md` (로드맵 Phase 1, 선행 의존 없음)
> 출처 스펙: [`docs/church-backend-spec.md`](../../church-backend-spec.md) §3·§4 (인가 키 §4.4, 에러 §5)
> 상위 로드맵: [`2026-06-04-church-backend-workflow-design.md`](./2026-06-04-church-backend-workflow-design.md)
> 선행: [#2 부트스트랩](./2026-06-04-phase1-bootstrap-design.md), [G2 공통·예외](./2026-06-04-g2-common-exception-design.md) (`ErrorResponse`·`ErrorCode`·`AuditorAware` 스텁을 재사용·확장)
> 후속: D4(auth) — 본 설계의 발급 유틸·토큰 저장소 **write 경로**를 호출해 완성

## 목표 / 성공 기준

스펙 §4의 인증·인가 토대를 `global/security`에 한 번에 못 박는다. **도메인(auth·member·role) 코드는 만들지 않는다** — 인프라(JWT 유틸·필터·SecurityConfig·RedisConfig·위계 유틸·메서드 보안)만 짓고, 컨트롤러가 없으므로 테스트 전용 컨트롤러로 검증한다.

성공 기준:
1. `JwtTokenProvider`가 access/refresh 토큰을 발급하고, 서명·만료·type·위변조를 검증한다(만료값은 `.env` 주입).
2. `JwtAuthenticationFilter`가 access 토큰 클레임에서 권한을 펼쳐 `SecurityContext`에 부여하고, Redis 블랙리스트(jti)를 **확인(read)** 한다.
3. SecurityConfig 경로 3분법이 선언된다: `/api/admin/**` 인증, `/api/gallery/**` `GALLERY_VIEW`, 그 외 `/api/**` 공개.
4. 인증·인가 실패(필터/경로/메서드)가 **G2와 동일한 RFC 7807 봉투**로 응답된다(`INVALID_TOKEN` 401 / `ACCESS_DENIED` 403).
5. `RoleHierarchyValidator`가 위계 4대 가드(priority·is_system·자기역할·마지막 SUPER_ADMIN)를 순수 함수로 강제하고, 전수 단위테스트 green.
6. `@EnableMethodSecurity`로 `@PreAuthorize`가 작동한다.
7. `AuditorAware<Long>`가 인증 회원의 `mid`(member.id)를 반환해 `created_by`/`updated_by`가 채워진다.
8. `global → domain` 의존 0 유지(ArchUnit green), 빌드·테스트 green, 신규 코드 커버리지 80%+.

## 핵심 결정

브레인스토밍에서 확정한 갈림길(권장안 채택):

1. **경로 인가 = "스펙 그대로"(공개 기본).** `anyRequest().authenticated()` 같은 default-deny를 쓰지 않는다. `/api/admin/**`는 `authenticated()`(세부 권한은 메서드 `@PreAuthorize`), `/api/gallery/**`는 `hasAuthority("GALLERY_VIEW")`, 인프라 경로는 permit, 그 외 `/api/**`는 `permitAll()`. 인증이 필요한 **비관리 엔드포인트**(`/api/members/me`, `/api/auth/logout`)는 각 도메인이 메서드 보안으로 자체 방어 → `global`이 도메인 경로를 추적하지 않아 템플릿 철학(코드 불변·도메인 추가 자유)에 부합한다.
2. **`mid` 클레임 추가.** JWT는 `sub=uuid`(외부 식별자) 외에 내부 `member.id`(Long)를 private claim `mid`로 싣는다. 필터가 `MemberPrincipal.id`에 담고 `AuditorAware`가 그 값을 반환 → **작성자 자동기록을 DB 조회 0회로** 달성. uuid 외부 비노출 원칙은 유지된다(토큰 소지자=본인만 봄, 모든 API는 uuid 사용).
3. **Redis 토큰 저장소: 컴포넌트 완성 + read만 배선.** `RedisConfig` + `TokenBlacklist`/`RefreshTokenStore`를 read·write 모두 구현하되, G3 필터는 블랙리스트 **확인(read)** 만 호출한다. write(로그인 시 refresh 저장, 로그아웃 시 블랙리스트 등록)는 D4가 같은 컴포넌트를 호출 — 인터페이스를 한 번에 고정한다.
4. **메서드 거부 매핑(fork 확정).** 메서드 `@PreAuthorize` 거부(`AuthorizationDeniedException`)는 `GlobalExceptionHandler`에 `@ExceptionHandler`를 추가해 403 `ACCESS_DENIED`로 매핑한다(경로 단계 핸들러와 출력 일치).
5. **CORS 최소 배선 포함.** 이미 정의돼 미사용인 `cors.allowed-origin` 프로퍼티를 `CorsConfigurationSource`로 배선한다(분리 프론트엔드 브라우저 호출 필수). SecurityConfig를 어차피 손대므로 G3에 포함.

## 범위 경계 (G3에서 하는 것 vs D4로 미루는 것)

| 영역 | G3 (이번) | D4 (auth, 후속) |
|---|---|---|
| JWT 발급 유틸 | `JwtTokenProvider.issueAccess/issueRefresh` 구현 | 로그인 성공 시 **호출** |
| JWT 검증·필터 | access 토큰 검증·권한 부여·블랙리스트 read | — |
| 경로 인가 | 3분법 선언 | — |
| 위계 유틸 | `RoleHierarchyValidator` + 단위테스트 | role·member 도메인이 **호출** |
| 메서드 보안 | `@EnableMethodSecurity` | 도메인이 `@PreAuthorize` 부착 |
| Redis | `RedisConfig` + 저장소 컴포넌트(read·write 메서드) | refresh **저장**·블랙리스트 **등록** 호출 |
| 에러 | `INVALID_TOKEN`/`ACCESS_DENIED` 배선 | `AUTHENTICATION_FAILED`(로그인 자격 불일치) 발생 |
| 엔드포인트 | **없음** (테스트 전용 컨트롤러로 검증) | `/api/auth/signup·login·refresh·logout` |

> D4 선행 의존이 `D3·G3`이므로, 위계 유틸·refresh write의 **호출자**(member/role 엔티티·레포지토리)는 D3에서 등장한다. G3은 호출자 없이 재사용 가능한 인프라 + 전수 테스트만 만든다.

## 산출물 (파일)

신규 — `global/security/`:

```
JwtProperties.java            // @ConfigurationProperties("jwt"): secret, accessExpiry, refreshExpiry (Duration/sec)
JwtTokenProvider.java         // 발급(access·refresh) + 파싱·검증 (jjwt 0.12.x)
MemberPrincipal.java          // record(Long id, String uuid, String name, int maxPriority) — SecurityContext principal
JwtAuthenticationFilter.java  // OncePerRequestFilter: 토큰 추출→검증→블랙리스트 read→SecurityContext 세팅
JwtAuthenticationEntryPoint.java  // 401 INVALID_TOKEN → RFC 7807 (ErrorResponse + ObjectMapper)
JwtAccessDeniedHandler.java       // 403 ACCESS_DENIED → RFC 7807
RoleHierarchyValidator.java   // priority 위계 4대 가드 (순수 컴포넌트, BusinessException 던짐)
SecurityAuditorAware.java     // AuditorAware<Long>: SecurityContext → MemberPrincipal.id (없으면 Optional.empty)
redis/RedisConfig.java        // RedisTemplate<String,String> / RedisConnectionFactory
redis/TokenBlacklist.java     // isBlacklisted(jti) [read, G3] / blacklist(jti, expiresAt) [write, D4] — 계약은 아래 "Redis 토큰 저장소 계약"
redis/RefreshTokenStore.java  // isValid(uuid, jti) [read] / save(uuid, jti, expiresAt)·revoke(uuid, jti)·revokeAll(uuid) [write, D4] — 다중 세션
```

수정 — 기존 파일:

- `global/config/SecurityConfig.java` — 셸 확장(경로 3분법·필터 등록·엔트리포인트/핸들러·CORS·`@EnableMethodSecurity`).
- `global/config/JpaConfig.java` — `auditorAwareRef`를 `SecurityAuditorAware`로 교체, 스텁 `@Bean auditorAware()` 제거(주석이 예고한 "본문만 교체").
- `global/exception/GlobalExceptionHandler.java` — `@ExceptionHandler(AuthorizationDeniedException.class)` → 403 `ACCESS_DENIED` 추가.

테스트 전용(`src/test`, 기존 `PageTestController`/`ExceptionTestController` 패턴):

- `SecuredTestController` — `/api/admin/test`, `/api/gallery/test`, `/api/public/test`, `/api/me-like/test`(`@PreAuthorize("isAuthenticated()")`)로 경로·메서드 보안 검증.

## JWT 계약 (claims)

| 클레임 | Access | Refresh | 비고 |
|---|---|---|---|
| `sub` | uuid | uuid | 외부 식별자 |
| `mid` | member.id (Long) | — | 내부 전용. AuditorAware·자기역할 검사용 |
| `name` | 표시명 | — | |
| `position` | 직분명 (nullable) | — | |
| `permissions` | `["SERMON_WRITE", …]` | — | 펼쳐진 권한 = GrantedAuthority |
| `maxPriority` | int | — | 위계 검증 입력 |
| `jti` | UUID | UUID | 블랙리스트/refresh 식별 키 |
| `type` | `"access"` | `"refresh"` | 필터는 access만 인정 |
| `iat`/`exp` | now / now+accessExpiry | now / now+refreshExpiry | |

- 서명: **HS256**, `Keys.hmacShaKeyFor(secret bytes)`. `JWT_SECRET`은 **32바이트(256bit) 이상** 필수(jjwt 강제). 미달 시 기동 거부(빠른 실패).
- 권한 매핑: `permissions` 각 항목을 `SimpleGrantedAuthority(name)`로 — **접두사 없음**(`hasAuthority('SERMON_WRITE')`, `hasAuthority('GALLERY_VIEW')`로 검사). `maxPriority`는 authority가 아니라 `MemberPrincipal` 필드.
- 필터는 `type≠access`인 토큰(예: refresh를 access 자리에 사용)을 `INVALID_TOKEN`으로 거부.
- refresh는 최소 클레임(`sub`·`jti`·`type`·`exp`)만 — 갱신 시 D4가 DB에서 최신 권한을 재조회한다(스펙 §4.1 "권한 변경은 다음 갱신 시 반영").

## Redis 토큰 저장소 계약 (세션 정책 · TTL · value schema)

G3이 **키 이름이 아니라 세션 정책·TTL 기준·value schema까지** 못 박는다 — D4(write 호출자)가 흔들리지 않도록. 스펙 §4.1은 "Refresh는 Redis 저장 / 로그아웃 시 블랙리스트"만 말하고 이 세 가지를 비워두므로 여기서 결정한다.

**세션 정책: 다중 세션.** 회원이 기기(폰·태블릿·교회 PC)마다 독립 refresh를 가진다. 새 기기 로그인이 기존 세션을 끊지 않아 고령 사용자의 "예기치 못한 로그아웃" 혼란을 피한다.

| 용도 | 키 | value | TTL | read(G3) / write(D4) |
|---|---|---|---|---|
| 로그아웃 블랙리스트 (access jti) | `auth:blacklist:{jti}` | `"1"` | **access exp − now** | read: `isBlacklisted(jti)` / write: `blacklist(jti, expiresAt)` |
| refresh 세션 (회원·기기별) | `auth:refresh:{uuid}:{jti}` | `"1"` | **refresh exp − now** | read: `isValid(uuid, jti)` / write: `save(uuid, jti, expiresAt)`·`revoke(uuid, jti)`·`revokeAll(uuid)` |

- **TTL 기준 = 토큰의 남은 수명.** 블랙리스트 TTL이 access 만료보다 짧으면 로그아웃한 토큰이 되살아나고, 길면 키가 잔류한다. 그래서 write 메서드는 `ttl`이 아니라 **`Instant expiresAt`**(토큰의 `exp`)를 받고 저장소 내부에서 `ttl = expiresAt − now`를 계산한다 — 호출자 오산 위험 제거.
- **블랙리스트는 access 토큰 전용**(필터가 access jti만 검사). refresh 무효화는 블랙리스트가 아니라 `revoke`(키 삭제)로 한다.
- **D4 매핑(참고):** 로그아웃 = 현재 access의 jti를 `blacklist(jti, accessExp)` + 그 기기 refresh를 `revoke(uuid, refreshJti)`. 전체 로그아웃·권한 즉시 반영(스펙 §4.1 "강제 만료") = `revokeAll(uuid)`. 단일 access의 jti와 refresh의 jti는 별개이며, 로그아웃이 어느 refresh를 끊을지 식별하는 흐름(요청 본문의 refresh 토큰 사용 등)은 D4가 정한다.
- **네임스페이스 상수**(`auth:blacklist:`, `auth:refresh:`)는 컴포넌트 상수로 고정해 G3·D4가 공유한다.

## 경로 인가 (3분법)

`SecurityFilterChain`:

| 경로 | 규칙 | 익명 | 인증·무권한 |
|---|---|---|---|
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/actuator/health`, `/error` | `permitAll` | 200 | — |
| `/api/admin/**` | `authenticated()` (세부 권한 = 메서드 `@PreAuthorize`) | 401 | 403 |
| `/api/gallery/**` | `hasAuthority("GALLERY_VIEW")` | 401 | 403 |
| 그 외 `/api/**` | `permitAll()` (공개 조회) | 200 | — |

- 체인 설정: `csrf` disable(기존), `STATELESS`(기존), form-login·httpBasic 비활성, `JwtAuthenticationFilter`를 `UsernamePasswordAuthenticationFilter` **앞에** 등록, `exceptionHandling`에 엔트리포인트·핸들러 연결, `cors` 활성(`CorsConfigurationSource`).
- `/api/admin/**`에 단일 권한을 못 박지 않는 이유: 관리 경로마다 필요한 권한이 다름(sermons→`SERMON_WRITE`, roles→`ROLE_MANAGE`). 경로 단계는 `authenticated()`(익명 401)까지만, 권한별 차단(403)은 메서드 `@PreAuthorize`가 담당.

## 에러 변환 매핑

필터·경로 인가 실패는 컨트롤러 진입 전이라 `@RestControllerAdvice`에 도달하지 못한다. 세 경로 모두 **동일한 `ErrorResponse` 봉투**로 수렴시킨다:

| 발생 지점 | 처리기 | status / errorCode |
|---|---|---|
| 토큰 없음/만료/위변조/잘못된 type, 보호 경로 익명 접근 | `JwtAuthenticationEntryPoint` | 401 `INVALID_TOKEN` |
| 경로 단계 권한 부족(예: gallery에 `GALLERY_VIEW` 없음) | `JwtAccessDeniedHandler` | 403 `ACCESS_DENIED` |
| 메서드 `@PreAuthorize` 거부(`AuthorizationDeniedException`) | `GlobalExceptionHandler` `@ExceptionHandler` | 익명→401 `INVALID_TOKEN` / 인증·권한부족→403 `ACCESS_DENIED` |
| 위계 위반(`RoleHierarchyValidator`→`BusinessException`) | `GlobalExceptionHandler`(기존) | 403 `ACCESS_DENIED` |

- 엔트리포인트/핸들러는 `ErrorResponse.of(...)` + 주입된 `ObjectMapper`로 직렬화(`application/problem+json` 또는 `application/json`, G2 응답과 동일 형식 유지).
- 토큰 문제는 일괄 `INVALID_TOKEN`(만료/위변조/형식 구분 없이) — 정보 노출 최소화. `AUTHENTICATION_FAILED`는 G3에서 사용하지 않음(D4 로그인 전용).

## priority 위계 검증 유틸

`RoleHierarchyValidator` — DB 의존 없는 순수 컴포넌트. 카운트·플래그는 **호출자(미래 role·member 도메인)가 주입**하고, 위반 시 `BusinessException(ACCESS_DENIED)`를 던진다.

1. **priority 가드:** `대상역할.priority < 요청자.maxPriority` 가 아니면 거부(같거나 높으면 escalation).
2. **is_system 보호:** `대상역할.isSystem == true` 면 수정/삭제/권한변경 거부(priority 무관).
3. **자기역할 보호:** `요청자.mid == 대상회원.mid` 인 역할 부여/회수 거부.
4. **마지막 SUPER_ADMIN 보호:** `대상이 SUPER_ADMIN && superAdminCount <= 1` 이면 회수/강등/삭제 거부.

메서드 형태(예): `validateAssignable(int requesterMaxPriority, int targetPriority)`, `validateMutable(int requesterMaxPriority, RoleView target)`, `validateNotSelf(long requesterMid, long targetMid)`, `validateNotLastSuperAdmin(boolean targetIsSuperAdmin, long superAdminCount)`. 호출자가 없는 G3에선 **유틸 + 전수 단위테스트**만 만든다.

## 테스트 전략 (TDD, RED→GREEN, 80%+)

- **단위(스프링 X):**
  - `JwtTokenProvider`: 발급↔파싱 round-trip, 만료 토큰 거부, 위변조 서명 거부, 잘못된 `type` 거부, 32바이트 미만 시크릿 거부.
  - `RoleHierarchyValidator`: 4대 가드 각각의 거부 + 정상 통과 경계값.
  - `MemberPrincipal`/권한 매핑: permissions→authorities, mid/maxPriority 보존.
- **슬라이스(MockMvc + Spring Security):** `SecuredTestController`로 `/api/admin/test`·`/api/gallery/test`·`/api/public/test`·`/api/me-like/test` × (토큰 없음/유효 access/권한부족/만료) 조합 → 200/401/403 및 RFC 7807 바디 검증. `TokenBlacklist`는 mock.
- **통합(@SpringBootTest + `TestcontainersConfiguration`, 실제 Redis 7-alpine):**
  - `JwtAuthenticationFilter`: 블랙리스트된 jti를 넣고 401 거부 확인. `RedisConfig` 빈 기동 확인.
  - `TokenBlacklist`(write 포함): `blacklist(jti, expiresAt)` 후 키 생성·존재 확인, 저장된 **TTL이 `expiresAt − now`에 근접**한지, `isBlacklisted`가 true.
  - `RefreshTokenStore`(write 포함): `save→isValid` true, `revoke→isValid` false, `revokeAll`이 같은 uuid의 모든 기기 키 제거, **uuid-jti 불일치 시 `isValid` false**(다른 회원의 jti로 통과 불가).
- **감사:** `SecurityAuditorAware`가 인증 컨텍스트에서 `mid`를 반환하고, 미인증 시 `Optional.empty()`인지(기존 `BaseEntityAuditingTest`와 정합).

## 의존성 / 빌드 영향

- 신규 라이브러리 **없음** — jjwt 0.12.6(api/impl/jackson)·data-redis·security가 이미 classpath(`build.gradle`). 코드 작성은 jjwt 0.12.x API(`Jwts.builder()…signWith(key)`, `Jwts.parser().verifyWith(key).build().parseSignedClaims()`)를 Context7로 재확인 후 진행.
- `application.yml`의 `jwt:`·`cors:` 섹션은 이미 존재 — `JwtProperties` 바인딩 + CORS 배선으로 비로소 사용된다.
- ArchUnit `global → domain` 규칙 유지(security는 domain 미참조; 위계 유틸은 primitives/view 객체만 받음).

## 미해결 / 구현 시 확인

1. **jjwt 0.12.x 정확한 API** — `signWith(SecretKey)` 알고리즘 자동 선택, `parser().verifyWith()` 예외 타입(`ExpiredJwtException`/`SignatureException`/`MalformedJwtException`) 분기. 구현 직전 Context7 확인.
2. **`AuthorizationDeniedException` 전파 경로** — Spring Security 6 메서드 보안 거부가 DispatcherServlet의 `@ExceptionHandler`로 잡히는지 슬라이스 테스트로 실증(안 잡히면 `AccessDeniedHandler` 위임으로 폴백, 출력은 동일).
3. **엔트리포인트 Content-Type** — `application/problem+json` vs `application/json`. G2 핸들러 출력과 일치시킨다(현재 `ResponseEntity` 기본 → `application/json`).
4. **D4 로그아웃 refresh 식별** — 다중 세션에서 로그아웃이 어느 `auth:refresh:{uuid}:{jti}`를 끊을지 식별하는 흐름(요청 본문 refresh 토큰 사용 등)은 D4 결정 사항. G3은 `revoke(uuid, jti)`·`revokeAll(uuid)` 계약만 제공한다.
5. SecurityConfig가 비대해지면(경로+CORS+빈) 응집 단위로 분리 검토(현 규모는 단일 파일 유지).

> Redis 토큰 저장소의 세션 정책·TTL 기준·value schema는 더 이상 미해결이 아니다 — 위 "Redis 토큰 저장소 계약" 절에서 확정(코드리뷰 G3-1·2·3 반영).
