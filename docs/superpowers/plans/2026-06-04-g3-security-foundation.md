# G3 보안 기반 (JWT 인증·경로 인가·위계 검증) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `global/security`에 JWT 발급·검증 유틸, 인증 필터, 경로 3분법 SecurityConfig, Redis 토큰 저장소(read 배선), priority 위계 검증 유틸, 메서드 보안, 작성자 자동기록(AuditorAware)을 구축한다 — 도메인 코드 없이 재사용 가능한 보안 토대.

**Architecture:** stateless JWT. 필터가 access 토큰 클레임에서 권한을 펼쳐 `SecurityContext`에 부여하고 Redis 블랙리스트(jti)를 확인한다. 경로 단계는 admin=인증·gallery=`GALLERY_VIEW`·그 외 공개, 세부 권한은 메서드 `@PreAuthorize`. 인증·인가 실패는 G2와 동일한 RFC 7807 봉투(`INVALID_TOKEN`/`ACCESS_DENIED`)로 응답. 발급·블랙리스트 등록의 **write 호출**은 D4(auth)가 본 컴포넌트를 재사용해 완성한다.

**Tech Stack:** Java 21, Spring Boot 4.0.6 (Spring Security 7), jjwt 0.12.6, Spring Data Redis (`StringRedisTemplate`), JUnit 5 + Testcontainers(postgres·redis, 이미 배선), AssertJ.

**Spec:** [`docs/superpowers/specs/2026-06-04-g3-security-foundation-design.md`](../specs/2026-06-04-g3-security-foundation-design.md)

---

## 설계 노트 (스펙 대비 결정)

- **`RedisConfig` 미생성 (YAGNI):** Spring Boot가 `spring.data.redis.*`로 `StringRedisTemplate`을 자동구성하므로 별도 config 불필요. 컴포넌트는 `StringRedisTemplate`을 직접 주입한다. (스펙 파일 목록의 `redis/RedisConfig.java`는 생략)
- **claim 이름 상수**는 `JwtTokenProvider`의 `public static final`에 두어 D4가 공유한다.
- **필터는 plain class** (`@Component` 아님) — `SecurityConfig`에서 `new` 하여 `addFilterBefore`로만 등록(서블릿 이중 등록 방지).
- **테스트 시크릿**은 `build.gradle` test 태스크의 `JWT_SECRET` env로 전역 주입(스펙 §10 "교회별 주입" 위배 아님 — 테스트 전용).
- **메서드 거부 매핑 정밀화(스펙 결정 #4 보완):** `@ExceptionHandler(AuthorizationDeniedException)`는 **익명이면 401 `INVALID_TOKEN`, 인증됐으나 권한부족이면 403 `ACCESS_DENIED`**로 분기한다. 익명 사용자를 무조건 403으로 떨구지 않기 위함(메서드 보안 `isAuthenticated()` 거부 케이스). 경로 단계 익명 거부는 그대로 EntryPoint(401)가 처리.
- **포맷팅:** 본 계획의 코드는 로직에 집중했으므로, 각 커밋 전(또는 최소 Task 12 빌드 전) `./gradlew spotlessApply`로 palantirJavaFormat·import 순서·미사용 import를 정리한다(`spotlessCheck`가 `build`에서 강제됨).

## 구현 반영 노트 (코드 = 진실 공급원)

아래 항목은 구현 + 2라운드 코드리뷰를 거쳐 **실제 코드와 계획 사이에 생긴 확정 변경**이다. 계획의 코드 블록은 해당 태스크에서 수정됐다.

1. **`RedisConfig` 미생성** — 설계 노트와 동일. 스펙 산출물 목록에서도 삭제 반영됨.
2. **`SecurityAuditorAware` plain class, `JpaConfig @Bean`으로 등록.** `@Component` 없음. `JpaConfig` 안의 `@Bean securityAuditorAware()` 메서드가 인스턴스를 생성한다. `@DataJpaTest` 슬라이스는 컴포넌트 스캔을 건너뛰므로 `@Configuration` 소속 `@Bean`이어야 슬라이스에서 주입된다. `@Component` + `@Bean` 이중 선언은 이름 충돌로 컨텍스트를 깬다. (Task 7 코드 블록 수정됨)
3. **`JwtProperties` `@Validated` + `@Positive`.** `accessExpiry`·`refreshExpiry`에 `@Positive` 제약 추가, 바인딩 시점에 검증. 0/음수 주입 시 기동 거부(fail-fast). 단위 테스트(`new` 직접 생성)에는 영향 없음. `JwtPropertiesValidationTest` 2 tests 추가. (Task 1 코드 블록 수정됨)
4. **`RefreshTokenStore.revokeAll` SCAN 사용.** `redis.keys(...)` 대신 `ScanOptions` + `Cursor<String>` 커서 기반 SCAN으로 교체. Redis keyspace를 캐시 등과 공유하므로 KEYS의 전체 블로킹을 피한다. (Task 6 코드 블록 수정됨)
5. **`SecurityConfig.corsConfigurationSource()` 와일드카드 원점 거부.** `cors.allowed-origin == "*"` 이면 `IllegalStateException`으로 기동 거부. credentialed CORS + wildcard origin은 브라우저·Spring 모두 거부하므로 모호한 런타임 오류 대신 명확한 fail-fast 선택. (Task 10 코드 블록 수정됨)
6. **`GlobalExceptionHandler` 익명 판별 3중 조건.** `authentication == null || authentication instanceof AnonymousAuthenticationToken || !authentication.isAuthenticated()` — `!isAuthenticated()` 절 추가. (Task 10 코드 블록 수정됨)
7. **Jackson 3 — `tools.jackson.databind.ObjectMapper`.** SB4 자동구성은 Jackson 3을 제공. `SecurityErrorResponses`, `JwtAuthenticationEntryPoint`, `JwtAccessDeniedHandler`, `SecurityErrorWritersTest`는 `tools.jackson.databind.ObjectMapper` import 사용. `@JsonInclude` 등 애너테이션은 `com.fasterxml.jackson.annotation` 유지. (Task 8 코드 블록 수정됨)
8. **`@AutoConfigureMockMvc` SB4 패키지.** `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (SB4 재배치). `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` 아님. Tasks 10·11 코드 블록 수정됨.
9. **`JwtAuthenticationFilter` `jti != null` 가드 + 6 tests.** `isAccess && jti != null && !tokenBlacklist.isBlacklisted(jti)` — null 방어 추가. 필터 테스트 6개(추가: `expired_token_leaves_context_empty`). authorities 검증: `.anyMatch(a -> a.getAuthority().equals("SERMON_WRITE"))`. (Task 9 코드 블록 수정됨)
10. **`SecurityConfigPathRulesTest` 9 tests.** gallery 익명 401, me 경로를 각각 독립 테스트로 분리. `JwtPropertiesValidationTest` 2 tests. (Task 10 코드 블록 수정됨)

## File Structure

신규 — `src/main/java/com/elipair/church/global/security/`:
- `JwtProperties.java` — `@ConfigurationProperties("jwt")` 레코드(secret·accessExpiry·refreshExpiry, 초 단위).
- `MemberPrincipal.java` — `record(Long id, String uuid, String name, int maxPriority)`, `SecurityContext` principal.
- `RoleHierarchyValidator.java` — 위계 4대 가드(순수 컴포넌트, `BusinessException(ACCESS_DENIED)`).
- `JwtTokenProvider.java` — 발급(access·refresh)·파싱·검증 + claim 상수.
- `redis/TokenBlacklist.java` — `isBlacklisted`(read)·`blacklist`(write).
- `redis/RefreshTokenStore.java` — `isValid`(read)·`save`·`revoke`·`revokeAll`(write).
- `SecurityAuditorAware.java` — `AuditorAware<Long>`, `SecurityContext`→`MemberPrincipal.id`.
- `SecurityErrorResponses.java` — 필터/핸들러 공용 RFC 7807 직렬화 헬퍼(package-private static).
- `JwtAuthenticationEntryPoint.java` — 401 `INVALID_TOKEN`.
- `JwtAccessDeniedHandler.java` — 403 `ACCESS_DENIED`.
- `JwtAuthenticationFilter.java` — `OncePerRequestFilter`.

수정 — 기존:
- `global/config/SecurityConfig.java` — 셸 확장(3분법·필터·엔트리포인트/핸들러·CORS·`@EnableMethodSecurity`·`@EnableConfigurationProperties`).
- `global/config/JpaConfig.java` — `auditorAwareRef`를 `securityAuditorAware`로, 스텁 `@Bean` 제거.
- `global/exception/GlobalExceptionHandler.java` — `@ExceptionHandler(AuthorizationDeniedException.class)` → 403.
- `build.gradle` — test 태스크에 `JWT_SECRET` env.

신규 테스트 — `src/test/java/com/elipair/church/global/security/` + 일부 `src/test/.../testfixture`.

---

### Task 1: JwtProperties + 테스트 시크릿 배선

**Files:**
- Create: `src/main/java/com/elipair/church/global/security/JwtProperties.java`
- Test: `src/test/java/com/elipair/church/global/security/JwtPropertiesTest.java`
- Modify: `build.gradle` (test 태스크), `global/config/SecurityConfig.java` (빈 등록)

- [ ] **Step 1: build.gradle test 태스크에 JWT_SECRET env 추가**

`build.gradle`의 `tasks.named('test') { ... }` 블록을 다음으로 교체:

```groovy
tasks.named('test') {
    useJUnitPlatform()
    // 테스트 전용 JWT 시크릿(32바이트 이상). 운영 값은 .env의 JWT_SECRET로 주입된다.
    environment 'JWT_SECRET', 'test-only-jwt-secret-please-change-0123456789'
    finalizedBy tasks.named('jacocoTestReport')
}
```

- [ ] **Step 2: 실패 테스트 작성** — `jwt` 프리픽스 바인딩(relaxed: `access-expiry`→`accessExpiry`)

```java
package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class JwtPropertiesTest {

    @Test
    void binds_kebab_case_properties() {
        var source = new MapConfigurationPropertySource(Map.of(
                "jwt.secret", "s3cr3t",
                "jwt.access-expiry", "3600",
                "jwt.refresh-expiry", "1209600"));

        JwtProperties props = new Binder(source).bind("jwt", JwtProperties.class).get();

        assertThat(props.secret()).isEqualTo("s3cr3t");
        assertThat(props.accessExpiry()).isEqualTo(3600);
        assertThat(props.refreshExpiry()).isEqualTo(1209600);
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.JwtPropertiesTest'`
Expected: FAIL — `JwtProperties` 클래스 없음(컴파일 에러).

- [ ] **Step 4: 구현**

```java
package com.elipair.church.global.security;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 설정(스펙 §4·§10). 만료값은 초 단위. secret은 HS256용 32바이트(256bit) 이상이어야 한다.
 *
 * <p>만료값은 @Positive로 기동 시 검증한다(0/음수 주입 시 발급 토큰이 즉시 만료돼 인증이 전부 깨지는 것을 fail-fast).
 * 바인딩 시점에만 검증되므로(@Validated) 레코드를 직접 생성하는 단위 테스트에는 영향이 없다.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        @Positive long accessExpiry,
        @Positive long refreshExpiry) {}
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.JwtPropertiesTest'`
Expected: PASS

- [ ] **Step 6: JwtProperties를 빈으로 등록** (이후 태스크의 통합 테스트가 `JwtTokenProvider` 빈을 생성할 수 있도록)

기존 `SecurityConfig.java`(이슈 #2 셸)에 import와 클래스 애너테이션을 추가한다. import 블록에:

```java
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.elipair.church.global.security.JwtProperties;
```

클래스 선언의 `@EnableWebSecurity` 아래에 추가:

```java
@EnableConfigurationProperties(JwtProperties.class)
```

(Task 10에서 SecurityConfig를 전면 교체할 때 이 애너테이션을 그대로 유지한다.)

- [ ] **Step 7: 전체 빌드로 무회귀 확인** (기존 @SpringBootTest가 새 프로퍼티 바인딩과 함께 기동되는지)

Run: `./gradlew spotlessApply && ./gradlew test`
Expected: PASS — 전 테스트 green(`JWT_SECRET` env가 `${JWT_SECRET}`를 해소).

- [ ] **Step 8: 커밋**

```bash
git add build.gradle src/main/java/com/elipair/church/global/security/JwtProperties.java src/main/java/com/elipair/church/global/config/SecurityConfig.java src/test/java/com/elipair/church/global/security/JwtPropertiesTest.java
git commit -m "feat : JWT 설정 프로퍼티·빈 등록·테스트 시크릿 배선 #4"
```

---

### Task 2: MemberPrincipal

**Files:**
- Create: `src/main/java/com/elipair/church/global/security/MemberPrincipal.java`
- Test: `src/test/java/com/elipair/church/global/security/MemberPrincipalTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberPrincipalTest {

    @Test
    void holds_identity_and_max_priority() {
        MemberPrincipal p = new MemberPrincipal(7L, "a3f8-uuid", "홍길동", 900);

        assertThat(p.id()).isEqualTo(7L);
        assertThat(p.uuid()).isEqualTo("a3f8-uuid");
        assertThat(p.name()).isEqualTo("홍길동");
        assertThat(p.maxPriority()).isEqualTo(900);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.MemberPrincipalTest'`
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: 구현**

```java
package com.elipair.church.global.security;

/**
 * 인증된 회원의 SecurityContext principal. uuid는 외부 식별자, id(member.id)는 내부 감사용(mid 클레임).
 * 권한은 principal이 아니라 Authentication의 authorities가 보유한다.
 */
public record MemberPrincipal(Long id, String uuid, String name, int maxPriority) {}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.MemberPrincipalTest'`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/global/security/MemberPrincipal.java src/test/java/com/elipair/church/global/security/MemberPrincipalTest.java
git commit -m "feat : SecurityContext principal(MemberPrincipal) 추가 #4"
```

---

### Task 3: RoleHierarchyValidator (위계 4대 가드)

**Files:**
- Create: `src/main/java/com/elipair/church/global/security/RoleHierarchyValidator.java`
- Test: `src/test/java/com/elipair/church/global/security/RoleHierarchyValidatorTest.java`

- [ ] **Step 1: 실패 테스트 작성** — 4대 가드 거부 + 정상 통과 경계값

```java
package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class RoleHierarchyValidatorTest {

    private final RoleHierarchyValidator validator = new RoleHierarchyValidator();

    @Test
    void assignable_rejects_equal_or_higher_priority() {
        assertThatThrownBy(() -> validator.validateAssignable(900, 900))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> validator.validateAssignable(900, 1000)).isInstanceOf(BusinessException.class);
    }

    @Test
    void assignable_allows_strictly_lower_priority() {
        assertThatCode(() -> validator.validateAssignable(900, 899)).doesNotThrowAnyException();
    }

    @Test
    void mutable_rejects_system_role_regardless_of_priority() {
        assertThatThrownBy(() -> validator.validateMutable(1000, 100, true)).isInstanceOf(BusinessException.class);
    }

    @Test
    void mutable_allows_non_system_lower_priority() {
        assertThatCode(() -> validator.validateMutable(1000, 100, false)).doesNotThrowAnyException();
    }

    @Test
    void rejects_changing_own_role() {
        assertThatThrownBy(() -> validator.validateNotSelf(7L, 7L)).isInstanceOf(BusinessException.class);
        assertThatCode(() -> validator.validateNotSelf(7L, 8L)).doesNotThrowAnyException();
    }

    @Test
    void protects_last_super_admin() {
        assertThatThrownBy(() -> validator.validateNotLastSuperAdmin(true, 1)).isInstanceOf(BusinessException.class);
        assertThatCode(() -> validator.validateNotLastSuperAdmin(true, 2)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateNotLastSuperAdmin(false, 1)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.RoleHierarchyValidatorTest'`
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: 구현**

```java
package com.elipair.church.global.security;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * priority 기반 위계 검증(스펙 §4.3). DB 의존 없는 순수 컴포넌트 — 카운트·플래그는 호출자(role·member 도메인)가 주입한다.
 * 위반 시 BusinessException(ACCESS_DENIED). 호출자는 D3·D4에서 등장한다.
 */
@Component
public class RoleHierarchyValidator {

    /** 대상 역할 priority가 요청자 maxPriority보다 strictly 낮아야 한다(escalation 차단). */
    public void validateAssignable(int requesterMaxPriority, int targetPriority) {
        if (targetPriority >= requesterMaxPriority) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "대상 역할의 priority가 요청자 권한 이상입니다");
        }
    }

    /** 역할 수정/삭제/권한변경: is_system 보호 + priority 가드. */
    public void validateMutable(int requesterMaxPriority, int targetPriority, boolean targetIsSystem) {
        if (targetIsSystem) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "시스템 역할은 수정·삭제할 수 없습니다");
        }
        validateAssignable(requesterMaxPriority, targetPriority);
    }

    /** 자기 자신의 역할은 부여/회수할 수 없다. */
    public void validateNotSelf(long requesterMemberId, long targetMemberId) {
        if (requesterMemberId == targetMemberId) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "자신의 역할은 변경할 수 없습니다");
        }
    }

    /** 마지막 SUPER_ADMIN 회수·강등·삭제 금지. */
    public void validateNotLastSuperAdmin(boolean targetIsSuperAdmin, long superAdminCount) {
        if (targetIsSuperAdmin && superAdminCount <= 1) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "마지막 SUPER_ADMIN은 회수·강등·삭제할 수 없습니다");
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.RoleHierarchyValidatorTest'`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/global/security/RoleHierarchyValidator.java src/test/java/com/elipair/church/global/security/RoleHierarchyValidatorTest.java
git commit -m "feat : priority 위계 검증 유틸 추가 #4"
```

---

### Task 4: JwtTokenProvider (발급·파싱·검증)

**Files:**
- Create: `src/main/java/com/elipair/church/global/security/JwtTokenProvider.java`
- Test: `src/test/java/com/elipair/church/global/security/JwtTokenProviderTest.java`

- [ ] **Step 1: 실패 테스트 작성** — round-trip / 만료 / 위변조 / 약한 시크릿

```java
package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789"; // >=32 bytes
    private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 3600, 1209600));

    @Test
    void access_token_round_trips_all_claims() {
        MemberPrincipal principal = new MemberPrincipal(7L, "uuid-7", "홍길동", 900);

        String token = provider.issueAccess(principal, "장로", List.of("SERMON_WRITE", "NOTICE_WRITE"));
        Claims claims = provider.parse(token);

        assertThat(claims.getSubject()).isEqualTo("uuid-7");
        assertThat(claims.get(JwtTokenProvider.CLAIM_MID, Long.class)).isEqualTo(7L);
        assertThat(claims.get(JwtTokenProvider.CLAIM_NAME, String.class)).isEqualTo("홍길동");
        assertThat(claims.get(JwtTokenProvider.CLAIM_POSITION, String.class)).isEqualTo("장로");
        assertThat(claims.get(JwtTokenProvider.CLAIM_MAX_PRIORITY, Integer.class)).isEqualTo(900);
        assertThat(claims.get(JwtTokenProvider.CLAIM_PERMISSIONS, List.class))
                .containsExactly("SERMON_WRITE", "NOTICE_WRITE");
        assertThat(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class)).isEqualTo(JwtTokenProvider.TYPE_ACCESS);
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void refresh_token_is_minimal_and_typed() {
        String token = provider.issueRefresh("uuid-7");
        Claims claims = provider.parse(token);

        assertThat(claims.getSubject()).isEqualTo("uuid-7");
        assertThat(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class)).isEqualTo(JwtTokenProvider.TYPE_REFRESH);
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get(JwtTokenProvider.CLAIM_PERMISSIONS, List.class)).isNull();
    }

    @Test
    void expired_token_is_rejected() {
        JwtTokenProvider expiring = new JwtTokenProvider(new JwtProperties(SECRET, -60, 1209600));
        String token = expiring.issueAccess(new MemberPrincipal(1L, "u", "n", 0), null, List.of());

        assertThatThrownBy(() -> provider.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tampered_signature_is_rejected() {
        String foreign = new JwtTokenProvider(new JwtProperties("another-secret-another-secret-0123456789", 3600, 1209600))
                .issueAccess(new MemberPrincipal(1L, "u", "n", 0), null, List.of());

        assertThatThrownBy(() -> provider.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void weak_secret_fails_fast_at_construction() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("too-short", 3600, 1209600)))
                .isInstanceOf(WeakKeyException.class);
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.JwtTokenProviderTest'`
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: 구현**

```java
package com.elipair.church.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 발급·파싱·검증(스펙 §4.2). HS256. sub=uuid, mid=member.id(내부), 펼쳐진 permissions·maxPriority.
 * 발급은 D4(auth)가 로그인 시 호출한다. parse는 서명·만료·형식을 검증하며 실패 시 JwtException 계열을 던진다.
 */
@Component
public class JwtTokenProvider {

    public static final String CLAIM_MID = "mid";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_POSITION = "position";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_MAX_PRIORITY = "maxPriority";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessExpiry;
    private final Duration refreshExpiry;

    public JwtTokenProvider(JwtProperties properties) {
        // 32바이트 미만이면 Keys.hmacShaKeyFor가 WeakKeyException으로 기동을 막는다(빠른 실패).
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessExpiry = Duration.ofSeconds(properties.accessExpiry());
        this.refreshExpiry = Duration.ofSeconds(properties.refreshExpiry());
    }

    public String issueAccess(MemberPrincipal principal, String position, List<String> permissions) {
        Date now = new Date();
        return Jwts.builder()
                .subject(principal.uuid())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpiry.toMillis()))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_MID, principal.id())
                .claim(CLAIM_NAME, principal.name())
                .claim(CLAIM_POSITION, position)
                .claim(CLAIM_PERMISSIONS, permissions)
                .claim(CLAIM_MAX_PRIORITY, principal.maxPriority())
                .signWith(key)
                .compact();
    }

    public String issueRefresh(String uuid) {
        Date now = new Date();
        return Jwts.builder()
                .subject(uuid)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpiry.toMillis()))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .signWith(key)
                .compact();
    }

    /** 서명·만료·형식 검증. 실패 시 JwtException 계열(Expired/Signature/Malformed) 또는 IllegalArgumentException. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.JwtTokenProviderTest'`
Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/global/security/JwtTokenProvider.java src/test/java/com/elipair/church/global/security/JwtTokenProviderTest.java
git commit -m "feat : JWT 발급·검증 유틸 추가 #4"
```

---

### Task 5: TokenBlacklist (Redis, read+write)

**Files:**
- Create: `src/main/java/com/elipair/church/global/security/redis/TokenBlacklist.java`
- Test: `src/test/java/com/elipair/church/global/security/redis/TokenBlacklistTest.java`

- [ ] **Step 1: 실패 테스트 작성** — 실제 Redis(Testcontainers). 저장→존재·TTL→만료 무시

```java
package com.elipair.church.global.security.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TokenBlacklistTest {

    @Autowired
    TokenBlacklist blacklist;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void blacklisted_jti_is_detected_with_ttl() {
        String jti = "jti-" + Instant.now().toEpochMilli();
        blacklist.blacklist(jti, Instant.now().plusSeconds(120));

        assertThat(blacklist.isBlacklisted(jti)).isTrue();
        Long ttl = redis.getExpire("auth:blacklist:" + jti);
        assertThat(ttl).isBetween(100L, 120L);
    }

    @Test
    void unknown_jti_is_not_blacklisted() {
        assertThat(blacklist.isBlacklisted("never-stored")).isFalse();
    }

    @Test
    void already_expired_expiresAt_is_not_stored() {
        String jti = "expired-" + Instant.now().toEpochMilli();
        blacklist.blacklist(jti, Instant.now().minusSeconds(10));

        assertThat(blacklist.isBlacklisted(jti)).isFalse();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.redis.TokenBlacklistTest'`
Expected: FAIL — `TokenBlacklist` 없음.

- [ ] **Step 3: 구현**

```java
package com.elipair.church.global.security.redis;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 로그아웃 access 토큰 블랙리스트(스펙 §4.1, G3 설계 "Redis 토큰 저장소 계약").
 * key=auth:blacklist:{jti}, value="1", TTL=토큰 남은 수명(expiresAt-now).
 * G3 필터는 isBlacklisted(read)만 호출. blacklist(write)는 D4 로그아웃이 호출한다.
 */
@Component
public class TokenBlacklist {

    static final String PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redis;

    public TokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void blacklist(String jti, Instant expiresAt) {
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).toSeconds();
        if (ttlSeconds <= 0) {
            return; // 이미 만료된 토큰 — 저장 불필요
        }
        redis.opsForValue().set(PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.redis.TokenBlacklistTest'`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/global/security/redis/TokenBlacklist.java src/test/java/com/elipair/church/global/security/redis/TokenBlacklistTest.java
git commit -m "feat : 로그아웃 토큰 블랙리스트 저장소 추가 #4"
```

---

### Task 6: RefreshTokenStore (Redis, 다중 세션)

**Files:**
- Create: `src/main/java/com/elipair/church/global/security/redis/RefreshTokenStore.java`
- Test: `src/test/java/com/elipair/church/global/security/redis/RefreshTokenStoreTest.java`

- [ ] **Step 1: 실패 테스트 작성** — save/isValid/revoke/revokeAll + uuid-jti 불일치

```java
package com.elipair.church.global.security.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RefreshTokenStoreTest {

    @Autowired
    RefreshTokenStore store;

    @Test
    void save_then_valid_then_revoke() {
        String uuid = "u-" + Instant.now().toEpochMilli();
        store.save(uuid, "jti-A", Instant.now().plusSeconds(600));

        assertThat(store.isValid(uuid, "jti-A")).isTrue();

        store.revoke(uuid, "jti-A");
        assertThat(store.isValid(uuid, "jti-A")).isFalse();
    }

    @Test
    void mismatched_uuid_or_jti_is_invalid() {
        String uuid = "u2-" + Instant.now().toEpochMilli();
        store.save(uuid, "jti-A", Instant.now().plusSeconds(600));

        assertThat(store.isValid(uuid, "jti-OTHER")).isFalse();
        assertThat(store.isValid("u-other", "jti-A")).isFalse();
    }

    @Test
    void revokeAll_removes_every_device_session() {
        String uuid = "u3-" + Instant.now().toEpochMilli();
        store.save(uuid, "jti-phone", Instant.now().plusSeconds(600));
        store.save(uuid, "jti-tablet", Instant.now().plusSeconds(600));

        store.revokeAll(uuid);

        assertThat(store.isValid(uuid, "jti-phone")).isFalse();
        assertThat(store.isValid(uuid, "jti-tablet")).isFalse();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.redis.RefreshTokenStoreTest'`
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: 구현**

```java
package com.elipair.church.global.security.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 다중 세션 Refresh 토큰 저장소(G3 설계 "Redis 토큰 저장소 계약").
 * key=auth:refresh:{uuid}:{jti}, value="1", TTL=refresh 남은 수명. 회원·기기별 독립 세션.
 * G3은 read(isValid)만 사용. save/revoke/revokeAll(write)은 D4 로그인·로그아웃이 호출한다.
 *
 * <p>revokeAll은 KEYS가 아니라 SCAN(커서 기반, 논블로킹)으로 키를 수집한다. Redis는 캐시·조회수 등과
 * keyspace를 공유하므로(스펙 §9) KEYS는 전체를 블로킹할 수 있다. SCAN은 그 위험을 피한다.
 */
@Component
public class RefreshTokenStore {

    static final String PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;

    public RefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(String uuid, String jti) {
        return PREFIX + uuid + ":" + jti;
    }

    public void save(String uuid, String jti, Instant expiresAt) {
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).toSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        redis.opsForValue().set(key(uuid, jti), "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isValid(String uuid, String jti) {
        return Boolean.TRUE.equals(redis.hasKey(key(uuid, jti)));
    }

    public void revoke(String uuid, String jti) {
        redis.delete(key(uuid, jti));
    }

    /** 전체 로그아웃·강제 만료(스펙 §4.1). SCAN으로 해당 회원의 세션 키만 수집해 삭제(KEYS 미사용 — 논블로킹). */
    public void revokeAll(String uuid) {
        ScanOptions options =
                ScanOptions.scanOptions().match(PREFIX + uuid + ":*").count(100).build();
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.redis.RefreshTokenStoreTest'`
Expected: PASS (3 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/global/security/redis/RefreshTokenStore.java src/test/java/com/elipair/church/global/security/redis/RefreshTokenStoreTest.java
git commit -m "feat : 다중 세션 Refresh 토큰 저장소 추가 #4"
```

---

### Task 7: SecurityAuditorAware + JpaConfig 교체

**Files:**
- Create: `src/main/java/com/elipair/church/global/security/SecurityAuditorAware.java`
- Modify: `src/main/java/com/elipair/church/global/config/JpaConfig.java`
- Test: `src/test/java/com/elipair/church/global/security/SecurityAuditorAwareTest.java`

- [ ] **Step 1: 실패 테스트 작성** — 인증 시 mid 반환, 미인증 시 empty

```java
package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityAuditorAwareTest {

    private final SecurityAuditorAware auditorAware = new SecurityAuditorAware();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returns_member_id_when_authenticated() {
        MemberPrincipal principal = new MemberPrincipal(42L, "uuid", "name", 100);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThat(auditorAware.getCurrentAuditor()).contains(42L);
    }

    @Test
    void empty_when_unauthenticated() {
        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.SecurityAuditorAwareTest'`
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: SecurityAuditorAware 구현**

```java
package com.elipair.church.global.security;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * BaseEntity의 created_by/updated_by를 채우는 감사자 공급원(스펙 §6, BaseEntity 주석 "#4부터 채움").
 * SecurityContext의 MemberPrincipal.id(=member.id)를 반환 — DB 조회 없음. 미인증이면 Optional.empty().
 * JpaConfig#securityAuditorAware()로 빈 등록 — 직접 @Component 불필요.
 */
public class SecurityAuditorAware implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.ofNullable(principal.id());
    }
}
```

- [ ] **Step 4: JpaConfig를 `@Bean` 등록 방식으로 교체**

`src/main/java/com/elipair/church/global/config/JpaConfig.java` 전체를 다음으로 교체. `@DataJpaTest` 슬라이스는 컴포넌트 스캔을 건너뛰므로 `@Component` 대신 `@Configuration` 소속 `@Bean`으로 등록해야 슬라이스에서 감사자가 주입된다:

```java
package com.elipair.church.global.config;

import com.elipair.church.global.security.SecurityAuditorAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화. createdAt/updatedAt는 자동, createdBy/updatedBy는 SecurityAuditorAware가 공급한다(#4).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
public class JpaConfig {

    @Bean
    public SecurityAuditorAware securityAuditorAware() {
        return new SecurityAuditorAware();
    }
}
```

- [ ] **Step 5: 통과 확인 + 기존 감사 테스트 무회귀**

Run: `./gradlew test --tests 'com.elipair.church.global.security.SecurityAuditorAwareTest' --tests 'com.elipair.church.global.common.BaseEntityAuditingTest'`
Expected: PASS — 신규 2건 + 기존 감사 테스트(미인증 컨텍스트라 createdBy=null 유지).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/elipair/church/global/security/SecurityAuditorAware.java src/main/java/com/elipair/church/global/config/JpaConfig.java src/test/java/com/elipair/church/global/security/SecurityAuditorAwareTest.java
git commit -m "feat : SecurityContext 기반 작성자 자동기록(AuditorAware) #4"
```

---

### Task 8: 인증 실패 RFC 7807 변환 (EntryPoint·AccessDeniedHandler)

**Files:**
- Create: `src/main/java/com/elipair/church/global/security/SecurityErrorResponses.java`
- Create: `src/main/java/com/elipair/church/global/security/JwtAuthenticationEntryPoint.java`
- Create: `src/main/java/com/elipair/church/global/security/JwtAccessDeniedHandler.java`
- Test: `src/test/java/com/elipair/church/global/security/SecurityErrorWritersTest.java`

- [ ] **Step 1: 실패 테스트 작성** — EntryPoint=401 INVALID_TOKEN, Handler=403 ACCESS_DENIED, RFC 7807 바디

```java
package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

class SecurityErrorWritersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void entry_point_writes_401_invalid_token() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/sermons");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationEntryPoint(mapper).commence(request, response, new BadCredentialsException("x"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"INVALID_TOKEN\"");
        assertThat(response.getContentAsString()).contains("\"instance\":\"/api/admin/sermons\"");
    }

    @Test
    void access_denied_handler_writes_403_access_denied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/gallery/albums");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAccessDeniedHandler(mapper).handle(request, response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"ACCESS_DENIED\"");
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.SecurityErrorWritersTest'`
Expected: FAIL — 클래스 없음.

- [ ] **Step 3: 공용 헬퍼 구현**

```java
package com.elipair.church.global.security;

import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/** 필터 단계 인증·인가 실패를 G2와 동일한 RFC 7807 봉투로 직렬화하는 공용 헬퍼. */
final class SecurityErrorResponses {

    private SecurityErrorResponses() {}

    static void write(HttpServletResponse response, HttpServletRequest request, ErrorCode code, ObjectMapper mapper)
            throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), ErrorResponse.of(code, request.getRequestURI()));
    }
}
```

- [ ] **Step 4: EntryPoint·Handler 구현**

```java
package com.elipair.church.global.security;

import com.elipair.church.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 미인증·토큰 문제(없음·만료·위변조·잘못된 type) → 401 INVALID_TOKEN. */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        SecurityErrorResponses.write(response, request, ErrorCode.INVALID_TOKEN, objectMapper);
    }
}
```

```java
package com.elipair.church.global.security;

import com.elipair.church.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 인증됐으나 경로 권한 부족 → 403 ACCESS_DENIED. */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        SecurityErrorResponses.write(response, request, ErrorCode.ACCESS_DENIED, objectMapper);
    }
}
```

- [ ] **Step 5: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.SecurityErrorWritersTest'`
Expected: PASS (2 tests)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/elipair/church/global/security/SecurityErrorResponses.java src/main/java/com/elipair/church/global/security/JwtAuthenticationEntryPoint.java src/main/java/com/elipair/church/global/security/JwtAccessDeniedHandler.java src/test/java/com/elipair/church/global/security/SecurityErrorWritersTest.java
git commit -m "feat : 인증·인가 실패 RFC 7807 변환(EntryPoint·AccessDeniedHandler) #4"
```

---

### Task 9: JwtAuthenticationFilter

**Files:**
- Create: `src/main/java/com/elipair/church/global/security/JwtAuthenticationFilter.java`
- Test: `src/test/java/com/elipair/church/global/security/JwtAuthenticationFilterTest.java`

- [ ] **Step 1: 실패 테스트 작성** — 유효 access→컨텍스트 세팅, 블랙리스트·refresh·손상·없음→컨텍스트 비움

```java
package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.elipair.church.global.security.redis.TokenBlacklist;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "filter-test-secret-filter-test-secret-0123";
    private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 3600, 1209600));
    private final TokenBlacklistStub blacklist = new TokenBlacklistStub();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, blacklist);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest withToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    @Test
    void valid_access_token_populates_context() throws Exception {
        String token =
                provider.issueAccess(new MemberPrincipal(7L, "uuid-7", "홍길동", 900), "장로", List.of("SERMON_WRITE"));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(withToken(token), new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(MemberPrincipal.class);
        assertThat(((MemberPrincipal) auth.getPrincipal()).id()).isEqualTo(7L);
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("SERMON_WRITE"));
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blacklisted_token_leaves_context_empty() throws Exception {
        String token = provider.issueAccess(new MemberPrincipal(7L, "uuid-7", "n", 0), null, List.of());
        blacklist.block(provider.parse(token).getId());

        filter.doFilter(withToken(token), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void refresh_token_used_as_access_is_rejected() throws Exception {
        String refresh = provider.issueRefresh("uuid-7");

        filter.doFilter(withToken(refresh), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void malformed_token_does_not_throw_and_leaves_context_empty() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(withToken("not-a-jwt"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void no_header_leaves_context_empty() throws Exception {
        filter.doFilter(withToken(null), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expired_token_leaves_context_empty() throws Exception {
        JwtTokenProvider expiring = new JwtTokenProvider(new JwtProperties(SECRET, -60, 1209600));
        String token = expiring.issueAccess(new MemberPrincipal(7L, "uuid-7", "n", 0), null, List.of());

        filter.doFilter(withToken(token), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** isBlacklisted만 제어하는 경량 스텁(실제 Redis 불필요). */
    static class TokenBlacklistStub extends TokenBlacklist {
        private final java.util.Set<String> blocked = new java.util.HashSet<>();

        TokenBlacklistStub() {
            super(null);
        }

        void block(String jti) {
            blocked.add(jti);
        }

        @Override
        public boolean isBlacklisted(String jti) {
            return blocked.contains(jti);
        }
    }
}
```

> Note: `TokenBlacklistStub`이 `super(null)`로 `StringRedisTemplate` 없이 생성되고 `isBlacklisted`를 오버라이드하므로 Redis가 필요 없다(생성자는 필드 대입만 한다).

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.JwtAuthenticationFilterTest'`
Expected: FAIL — `JwtAuthenticationFilter` 없음.

- [ ] **Step 3: 구현**

```java
package com.elipair.church.global.security;

import com.elipair.church.global.security.redis.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * access 토큰을 검증해 SecurityContext에 권한을 부여한다(스펙 §4.3).
 * 토큰이 없거나(공개 경로 통과), 만료·위변조·refresh-type·블랙리스트면 컨텍스트를 비워 둔다 —
 * 보호 경로면 이후 EntryPoint가 401 INVALID_TOKEN으로 응답한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String BEARER = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklist tokenBlacklist;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, TokenBlacklist tokenBlacklist) {
        this.tokenProvider = tokenProvider;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Claims claims = tokenProvider.parse(token);
                String jti = claims.getId();
                boolean isAccess =
                        JwtTokenProvider.TYPE_ACCESS.equals(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class));
                if (isAccess && jti != null && !tokenBlacklist.isBlacklisted(jti)) {
                    SecurityContextHolder.getContext().setAuthentication(toAuthentication(claims));
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // 유효하지 않은 토큰 — 컨텍스트를 비워 둔다(보호 경로면 EntryPoint가 401 처리).
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(BEARER)) {
            return header.substring(BEARER.length());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Authentication toAuthentication(Claims claims) {
        List<String> permissions = claims.get(JwtTokenProvider.CLAIM_PERMISSIONS, List.class);
        List<SimpleGrantedAuthority> authorities = permissions == null
                ? List.of()
                : permissions.stream().map(SimpleGrantedAuthority::new).toList();
        Integer maxPriority = claims.get(JwtTokenProvider.CLAIM_MAX_PRIORITY, Integer.class);
        MemberPrincipal principal = new MemberPrincipal(
                claims.get(JwtTokenProvider.CLAIM_MID, Long.class),
                claims.getSubject(),
                claims.get(JwtTokenProvider.CLAIM_NAME, String.class),
                maxPriority == null ? 0 : maxPriority);
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.JwtAuthenticationFilterTest'`
Expected: PASS (6 tests)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/global/security/JwtAuthenticationFilter.java src/test/java/com/elipair/church/global/security/JwtAuthenticationFilterTest.java
git commit -m "feat : JWT 인증 필터 추가 #4"
```

---

### Task 10: SecurityConfig 배선 + 메서드 거부 매핑 + 경로/메서드 인가 검증

**Files:**
- Modify: `src/main/java/com/elipair/church/global/config/SecurityConfig.java`
- Modify: `src/main/java/com/elipair/church/global/exception/GlobalExceptionHandler.java`
- Create (test): `src/test/java/com/elipair/church/global/security/SecuredTestController.java`
- Create (test): `src/test/java/com/elipair/church/global/security/SecurityConfigPathRulesTest.java`

- [ ] **Step 1: 테스트 전용 컨트롤러 작성**

```java
package com.elipair.church.global.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** G3 경로·메서드 인가 검증용 테스트 컨트롤러(도메인 컨트롤러 부재 대체). */
@RestController
public class SecuredTestController {

    @GetMapping("/api/public/ping")
    public String publicPing() {
        return "public";
    }

    @GetMapping("/api/admin/ping")
    @PreAuthorize("hasAuthority('SERMON_WRITE')")
    public String adminPing() {
        return "admin";
    }

    @GetMapping("/api/gallery/ping")
    public String galleryPing() {
        return "gallery";
    }

    @GetMapping("/api/me/ping")
    @PreAuthorize("isAuthenticated()")
    public String mePing() {
        return "me";
    }
}
```

- [ ] **Step 2: 실패 테스트 작성** — 경로 3분법 + 메서드 보안 (200/401/403)

```java
package com.elipair.church.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SecuredTestController.class})
class SecurityConfigPathRulesTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider provider;

    private String bearer(List<String> permissions) {
        return "Bearer " + provider.issueAccess(new MemberPrincipal(1L, "u", "n", 100), null, permissions);
    }

    @Test
    void public_path_is_open_without_token() throws Exception {
        mockMvc.perform(get("/api/public/ping")).andExpect(status().isOk());
    }

    @Test
    void admin_path_anonymous_is_401_invalid_token() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void admin_path_without_permission_is_403_access_denied() throws Exception {
        mockMvc.perform(get("/api/admin/ping").header("Authorization", bearer(List.of("NOTICE_WRITE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void admin_path_with_permission_is_200() throws Exception {
        mockMvc.perform(get("/api/admin/ping").header("Authorization", bearer(List.of("SERMON_WRITE"))))
                .andExpect(status().isOk());
    }

    @Test
    void gallery_path_without_gallery_view_is_403() throws Exception {
        mockMvc.perform(get("/api/gallery/ping").header("Authorization", bearer(List.of("SERMON_WRITE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void gallery_path_with_gallery_view_is_200() throws Exception {
        mockMvc.perform(get("/api/gallery/ping").header("Authorization", bearer(List.of("GALLERY_VIEW"))))
                .andExpect(status().isOk());
    }

    @Test
    void gallery_path_anonymous_is_401_invalid_token() throws Exception {
        mockMvc.perform(get("/api/gallery/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void me_path_anonymous_is_401() throws Exception {
        mockMvc.perform(get("/api/me/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void me_path_authenticated_is_200() throws Exception {
        mockMvc.perform(get("/api/me/ping").header("Authorization", bearer(List.of())))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.SecurityConfigPathRulesTest'`
Expected: FAIL — 현재 SecurityConfig는 `anyRequest().authenticated()`라 공개 경로가 401, 필터·메서드 보안 미배선.

- [ ] **Step 4: GlobalExceptionHandler에 메서드 거부 매핑 추가**

`GlobalExceptionHandler.java`의 import 블록에 추가:

```java
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
```

`handleBusiness` 메서드 **바로 위**에 핸들러 추가. 메서드 보안(`@PreAuthorize`) 거부는 익명/인증 상태에 따라 401·403으로 분기한다(익명을 403으로 떨구지 않기 위함 — `isAuthenticated()` 메서드 가드 케이스):

```java
    /** 메서드 보안(@PreAuthorize) 거부 — 익명이면 401 INVALID_TOKEN, 인증됐으나 권한부족이면 403 ACCESS_DENIED. */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException e, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated();
        ErrorCode code = anonymous ? ErrorCode.INVALID_TOKEN : ErrorCode.ACCESS_DENIED;
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, request.getRequestURI()));
    }
```

- [ ] **Step 5: SecurityConfig 전체 교체**

`src/main/java/com/elipair/church/global/config/SecurityConfig.java`:

```java
package com.elipair.church.global.config;

import com.elipair.church.global.security.JwtAccessDeniedHandler;
import com.elipair.church.global.security.JwtAuthenticationEntryPoint;
import com.elipair.church.global.security.JwtAuthenticationFilter;
import com.elipair.church.global.security.JwtProperties;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.redis.TokenBlacklist;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 경로 3분법(스펙 §4.3): /api/admin/** 인증(세부 권한은 메서드 @PreAuthorize), /api/gallery/** GALLERY_VIEW,
 * 그 외 공개. JWT 필터·인증 실패 RFC 7807 변환·CORS·메서드 보안을 배선한다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklist tokenBlacklist;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final String corsAllowedOrigin;

    public SecurityConfig(
            JwtTokenProvider tokenProvider,
            TokenBlacklist tokenBlacklist,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler,
            @Value("${cors.allowed-origin}") String corsAllowedOrigin) {
        this.tokenProvider = tokenProvider;
        this.tokenBlacklist = tokenBlacklist;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsAllowedOrigin = corsAllowedOrigin;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/actuator/health")
                        .permitAll()
                        .requestMatchers("/api/admin/**")
                        .authenticated()
                        .requestMatchers("/api/gallery/**")
                        .hasAuthority("GALLERY_VIEW")
                        .anyRequest()
                        .permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new JwtAuthenticationFilter(tokenProvider, tokenBlacklist),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        // allowCredentials(true)는 와일드카드 origin과 공존할 수 없다(브라우저·Spring 모두 거부).
        // 운영자가 CORS_ALLOWED_ORIGIN=*로 잘못 넣으면 모호한 런타임 오류 대신 기동 시 명확히 실패시킨다.
        if ("*".equals(corsAllowedOrigin)) {
            throw new IllegalStateException(
                    "cors.allowed-origin은 '*'일 수 없습니다(credentialed CORS). CORS_ALLOWED_ORIGIN에 교회 프론트 도메인을 지정하세요.");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(corsAllowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

- [ ] **Step 6: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.SecurityConfigPathRulesTest'`
Expected: PASS (9 tests). 분기 책임: **경로 단계** 거부(admin 익명·gallery 권한부족·gallery 익명)는 EntryPoint(401)/AccessDeniedHandler(403)가, **메서드 단계** 거부(`@PreAuthorize`)는 Step 4의 `@ExceptionHandler`가 익명→401·인증→403으로 처리한다. 만약 메서드 거부가 500이 되면 spec 미해결 #2(전파 경로) 재확인 — 단 `@ExceptionHandler(AuthorizationDeniedException)`가 DispatcherServlet 단계에서 잡으므로 정상 동작해야 한다.

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/elipair/church/global/config/SecurityConfig.java src/main/java/com/elipair/church/global/exception/GlobalExceptionHandler.java src/test/java/com/elipair/church/global/security/SecuredTestController.java src/test/java/com/elipair/church/global/security/SecurityConfigPathRulesTest.java
git commit -m "feat : SecurityConfig 경로 3분법·메서드 보안·CORS 배선 #4"
```

---

### Task 11: 블랙리스트 e2e + 작성자 자동기록 end-to-end

**Files:**
- Create (test): `src/test/java/com/elipair/church/global/security/SecurityBlacklistE2eTest.java`

- [ ] **Step 1: 실패 테스트 작성** — 실제 Redis 블랙리스트된 토큰이 보호 경로에서 401

```java
package com.elipair.church.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.security.redis.TokenBlacklist;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SecuredTestController.class})
class SecurityBlacklistE2eTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider provider;

    @Autowired
    TokenBlacklist blacklist;

    @Test
    void blacklisted_access_token_is_rejected_on_protected_path() throws Exception {
        String token = provider.issueAccess(new MemberPrincipal(1L, "u", "n", 100), null, List.of("SERMON_WRITE"));
        Claims claims = provider.parse(token);
        blacklist.blacklist(claims.getId(), claims.getExpiration().toInstant());

        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void non_blacklisted_token_still_works() throws Exception {
        String token = provider.issueAccess(new MemberPrincipal(1L, "u", "n", 100), null, List.of("SERMON_WRITE"));

        mockMvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: 실패 확인 → 통과 확인** (구현은 이미 완료, 통합 검증만)

Run: `./gradlew test --tests 'com.elipair.church.global.security.SecurityBlacklistE2eTest'`
Expected: PASS (2 tests). 실패하면 필터의 블랙리스트 분기(Task 9) 또는 Redis 배선을 점검.

- [ ] **Step 3: 커밋**

```bash
git add src/test/java/com/elipair/church/global/security/SecurityBlacklistE2eTest.java
git commit -m "test : 블랙리스트 토큰 거부 e2e 검증 #4"
```

---

### Task 12: 전체 빌드·커버리지·아키텍처 검증

**Files:** 없음(검증 전용)

- [ ] **Step 1: 전체 빌드 + 테스트**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL — 전 테스트 green, ArchUnit `global → domain` 무위반(security는 domain 미참조), spotless 통과.

- [ ] **Step 2: 커버리지 확인**

Run: `./gradlew jacocoTestReport`
Expected: `build/reports/jacoco/test/html/index.html`에서 `global.security` 패키지 80%+ 확인.

- [ ] **Step 3: (커버리지 부족 시) 누락 분기 테스트 보강 후 재실행** — 부족하면 해당 클래스의 미커버 분기에 테스트 추가, 충분하면 스킵.

- [ ] **Step 4: 최종 커밋(보강 시에만)**

```bash
git add -A
git commit -m "test : G3 보안 기반 커버리지 보강 #4"
```
