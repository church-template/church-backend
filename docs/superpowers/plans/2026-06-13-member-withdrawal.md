# 회원탈퇴(자가탈퇴) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인한 본인이 자기 계정을 탈퇴(soft delete + 개인정보 스크럽 + 전체 세션 무효화)할 수 있는 `DELETE /api/members/me`를 추가한다.

**Architecture:** 탈퇴는 회원 생명주기 이벤트라 `MemberService.withdraw()`가 주도한다. 비밀번호 재인증 → 마지막 SUPER_ADMIN 가드 → `Member.withdraw()`(softDelete+PII 스크럽) → `RefreshTokenStore.revokeAll(uuid)` + access 토큰 블랙리스트 순서. access 토큰 블랙리스트 로직은 `AccessTokenBlacklister`(global/security)로 추출해 로그아웃과 공유한다. 스키마 변경 없음(기존 컬럼만 사용).

**Tech Stack:** Spring Boot 4.0.6, Java 21, JPA/PostgreSQL, Redis(Lettuce), JWT(jjwt), JUnit5 + MockMvc + Testcontainers, Mockito, Spotless(palantirJavaFormat).

**선행 조건:** 통합 테스트는 Testcontainers(postgres/redis)를 쓰므로 Docker가 실행 중이어야 한다. Spotless를 쓰므로 커밋 전 `./gradlew spotlessApply`로 포맷·미사용 import를 정리한다.

**설계 문서:** `docs/superpowers/specs/2026-06-13-member-withdrawal-design.md`

---

### Task 1: `AccessTokenBlacklister` 추출 + 로그아웃 재배선

access 토큰 파싱→jti 블랙리스트 로직(현재 `AuthService.blacklistAccess` private)을 공용 컴포넌트로 추출하고, `AuthService.logout`이 이를 쓰도록 교체한다. 회원탈퇴가 동일 로직을 재사용하기 위한 토대.

**Files:**
- Create: `src/main/java/com/elipair/church/global/security/AccessTokenBlacklister.java`
- Create: `src/test/java/com/elipair/church/global/security/AccessTokenBlacklisterTest.java`
- Modify: `src/main/java/com/elipair/church/domain/auth/AuthService.java`
- Modify: `src/test/java/com/elipair/church/domain/auth/AuthServiceTest.java`

- [ ] **Step 1: 실패 테스트 작성** — `AccessTokenBlacklisterTest.java`

```java
package com.elipair.church.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.global.security.redis.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessTokenBlacklisterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private TokenBlacklist tokenBlacklist;

    @InjectMocks
    private AccessTokenBlacklister blacklister;

    @Test
    void blacklists_jti_of_valid_access_token() {
        Claims claims = mock(Claims.class);
        when(tokenProvider.parse("acc")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));

        blacklister.blacklist("acc");

        verify(tokenBlacklist).blacklist(eq("jti-1"), any());
    }

    @Test
    void ignores_invalid_token() {
        when(tokenProvider.parse("bad")).thenThrow(new JwtException("bad"));

        blacklister.blacklist("bad");

        verify(tokenBlacklist, never()).blacklist(any(), any());
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.AccessTokenBlacklisterTest'`
Expected: 컴파일 실패(`AccessTokenBlacklister` 미존재).

- [ ] **Step 3: 구현** — `AccessTokenBlacklister.java`

```java
package com.elipair.church.global.security;

import com.elipair.church.global.security.redis.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;

/**
 * access 토큰을 파싱해 jti를 블랙리스트에 등록한다(로그아웃·회원탈퇴 공용).
 * 파싱 실패(만료/위조)는 방어적으로 무시 — 인증 필터를 통과한 토큰이라 도달이 드물다.
 */
@Component
public class AccessTokenBlacklister {

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklist tokenBlacklist;

    public AccessTokenBlacklister(JwtTokenProvider tokenProvider, TokenBlacklist tokenBlacklist) {
        this.tokenProvider = tokenProvider;
        this.tokenBlacklist = tokenBlacklist;
    }

    public void blacklist(String accessToken) {
        try {
            Claims claims = tokenProvider.parse(accessToken);
            if (claims.getId() != null) {
                tokenBlacklist.blacklist(claims.getId(), claims.getExpiration().toInstant());
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // 도달 드묾(필터 통과 토큰) — 방어적 무시
        }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.AccessTokenBlacklisterTest'`
Expected: PASS (2 tests)

- [ ] **Step 5: `AuthService` 재배선** — `AuthService.java`

(a) import 교체: `import com.elipair.church.global.security.redis.TokenBlacklist;` 줄을 삭제하고 `import com.elipair.church.global.security.AccessTokenBlacklister;` 추가.

(b) 필드 교체:
```java
    private final TokenBlacklist tokenBlacklist;
```
→
```java
    private final AccessTokenBlacklister accessTokenBlacklister;
```

(c) 생성자 교체 (전체):
```java
    public AuthService(
            MemberRepository memberRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            RefreshTokenStore refreshTokenStore,
            AccessTokenBlacklister accessTokenBlacklister) {
        this.memberRepository = memberRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.accessTokenBlacklister = accessTokenBlacklister;
    }
```

(d) `logout` 본문에서 `blacklistAccess(accessToken);`를 `accessTokenBlacklister.blacklist(accessToken);`로 교체.

(e) private 메서드 `blacklistAccess(...)` 전체 삭제(이제 헬퍼가 담당).

- [ ] **Step 6: `AuthServiceTest` 갱신** — `AuthServiceTest.java`

(a) import: `import com.elipair.church.global.security.redis.TokenBlacklist;` 삭제, `import com.elipair.church.global.security.AccessTokenBlacklister;` 추가.

(b) `@Mock private TokenBlacklist tokenBlacklist;` → `@Mock private AccessTokenBlacklister accessTokenBlacklister;`

(c) 두 logout 테스트를 아래로 전체 교체 (access는 더 이상 `AuthService`에서 파싱하지 않으므로 access Claims 스텁 제거):
```java
    @Test
    void logout_blacklists_access_and_revokes_owned_refresh() {
        Claims refresh = mock(Claims.class);
        when(tokenProvider.parse("refresh-tok")).thenReturn(refresh);
        when(refresh.get(JwtTokenProvider.CLAIM_TYPE, String.class)).thenReturn("refresh");
        when(refresh.getSubject()).thenReturn("uuid-1");
        when(refresh.getId()).thenReturn("rjti");

        authService.logout(new MemberPrincipal(1L, "uuid-1", "n", 100), "access-tok", "refresh-tok");

        verify(accessTokenBlacklister).blacklist("access-tok");
        verify(refreshTokenStore).revoke("uuid-1", "rjti");
    }

    @Test
    void logout_skips_revoke_for_other_users_refresh() {
        Claims refresh = mock(Claims.class);
        when(tokenProvider.parse("other-tok")).thenReturn(refresh);
        when(refresh.get(JwtTokenProvider.CLAIM_TYPE, String.class)).thenReturn("refresh");
        when(refresh.getSubject()).thenReturn("uuid-OTHER");
        lenient().when(refresh.getId()).thenReturn("rjti");

        authService.logout(new MemberPrincipal(1L, "uuid-1", "n", 100), "access-tok", "other-tok");

        verify(accessTokenBlacklister).blacklist("access-tok");
        verify(refreshTokenStore, never()).revoke(any(), any());
    }
```

- [ ] **Step 7: 포맷 + 전체 인증 테스트 통과 확인**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.auth.*' --tests 'com.elipair.church.global.security.AccessTokenBlacklisterTest'`
Expected: PASS (logout 회귀 포함 전부 green). `spotlessApply`가 미사용 import(`eq`/`Date` 등)를 자동 정리.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/elipair/church/global/security/AccessTokenBlacklister.java \
        src/test/java/com/elipair/church/global/security/AccessTokenBlacklisterTest.java \
        src/main/java/com/elipair/church/domain/auth/AuthService.java \
        src/test/java/com/elipair/church/domain/auth/AuthServiceTest.java
git commit -m "refactor : access 토큰 블랙리스트 로직을 AccessTokenBlacklister로 추출 #38"
```

---

### Task 2: `Member.withdraw()` 도메인 메서드

soft delete + PII 스크럽을 캡슐화하는 엔티티 메서드.

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/member/Member.java`
- Modify: `src/test/java/com/elipair/church/domain/member/MemberTest.java`

- [ ] **Step 1: 실패 테스트 작성** — `MemberTest.java`에 메서드 추가

```java
    @Test
    void withdraw_softdeletes_and_scrubs_pii() {
        Member m = signup();

        m.withdraw();

        assertThat(m.isDeleted()).isTrue();
        assertThat(m.getPhone()).isEqualTo("(탈퇴)");
        assertThat(m.getName()).isEqualTo("(탈퇴한 사용자)");
        assertThat(m.getEmail()).isNull();
        assertThat(m.getPassword()).isEqualTo("(withdrawn)");
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.MemberTest'`
Expected: 컴파일 실패(`withdraw()` 미존재).

- [ ] **Step 3: 구현** — `Member.java`

(a) 클래스 본문 상단(`public class Member extends BaseTimeEntity {` 바로 다음 줄)에 상수 추가:
```java
    private static final String WITHDRAWN_PHONE = "(탈퇴)";
    private static final String WITHDRAWN_NAME = "(탈퇴한 사용자)";
    private static final String WITHDRAWN_CREDENTIAL = "(withdrawn)"; // 비-BCrypt 센티넬 → matches() 항상 false
```

(b) 기존 `softDelete()` 메서드 바로 다음에 추가:
```java
    /** 자가탈퇴: 소프트삭제 + 개인정보(PII) 스크럽. 표시는 deletedAt 기준 마스킹이라 스크럽값과 무관. */
    public void withdraw() {
        softDelete();
        this.phone = WITHDRAWN_PHONE;
        this.name = WITHDRAWN_NAME;
        this.email = null;
        this.password = WITHDRAWN_CREDENTIAL;
    }
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.MemberTest'`
Expected: PASS

- [ ] **Step 5: 포맷 + 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/domain/member/Member.java \
        src/test/java/com/elipair/church/domain/member/MemberTest.java
git commit -m "feat : Member.withdraw() 소프트삭제+PII 스크럽 도메인 메서드 #38"
```

---

### Task 3: 자가탈퇴 API (`DELETE /api/members/me`) — 정상/재인증/검증

엔드포인트 + 서비스 오케스트레이션(재인증·스크럽·전체 세션 무효화)을 구현한다. SUPER_ADMIN 가드는 Task 4에서 추가.

**Files:**
- Create: `src/main/java/com/elipair/church/domain/member/dto/WithdrawRequest.java`
- Modify: `src/main/java/com/elipair/church/domain/member/controller/MeController.java`
- Modify: `src/main/java/com/elipair/church/domain/member/MemberService.java`
- Create: `src/test/java/com/elipair/church/domain/member/WithdrawApiTest.java`

- [ ] **Step 1: 실패 테스트 작성** — `WithdrawApiTest.java`

```java
package com.elipair.church.domain.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WithdrawApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanup() {
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private Role role(String name) {
        return roleRepository.findAll().stream()
                .filter(r -> r.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private void persistMember(String phone, String rawPassword, String roleName) {
        Member m = Member.create(phone, "홍길동", passwordEncoder.encode(rawPassword), "a@b.com", null, true, true);
        m.grantRole(role(roleName));
        memberRepository.saveAndFlush(m);
    }

    private String[] login(String phone, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var tokens = objectMapper.readTree(body).path("tokens");
        return new String[] {tokens.path("accessToken").asText(), tokens.path("refreshToken").asText()};
    }

    @Test
    void withdraw_softdeletes_scrubs_and_revokes_all_sessions() throws Exception {
        persistMember("01012345678", "password123", "MEMBER");
        String[] t = login("01012345678", "password123");
        String access = t[0];
        String refresh = t[1];

        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isNoContent());

        // 블랙리스트된 access → 보호 경로 401
        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());

        // revokeAll → refresh 무효 → 재발급 401
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // 탈퇴(soft delete + 번호 스크럽) → 동일 번호 재가입 가능(새 계정, 201)
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"phone\":\"010-1234-5678\",\"name\":\"새사람\",\"password\":\"newpass123\",\"termsAgreed\":true,\"privacyAgreed\":true}"))
                .andExpect(status().isCreated());
    }

    @Test
    void withdraw_with_wrong_password_is_401() throws Exception {
        persistMember("01012345678", "password123", "MEMBER");
        String[] t = login("01012345678", "password123");

        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + t[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void withdraw_without_password_is_400() throws Exception {
        persistMember("01012345678", "password123", "MEMBER");
        String[] t = login("01012345678", "password123");

        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + t[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.WithdrawApiTest'`
Expected: 컴파일 실패(`WithdrawRequest`·`DELETE` 핸들러·`withdraw` 서비스 미존재).

- [ ] **Step 3: DTO 생성** — `WithdrawRequest.java`

```java
package com.elipair.church.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

/** 자가탈퇴 요청. 현재 비밀번호로 재인증. */
public record WithdrawRequest(@NotBlank String password) {}
```

- [ ] **Step 4: 서비스 구현** — `MemberService.java`

(a) import 추가 (서비스는 `String password`를 받으므로 `WithdrawRequest` import는 필요 없음):
```java
import com.elipair.church.global.security.AccessTokenBlacklister;
import com.elipair.church.global.security.redis.RefreshTokenStore;
```

(b) 필드 2개 추가(기존 필드 아래):
```java
    private final RefreshTokenStore refreshTokenStore;
    private final AccessTokenBlacklister accessTokenBlacklister;
```

(c) 생성자 교체(전체):
```java
    public MemberService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenStore refreshTokenStore,
            AccessTokenBlacklister accessTokenBlacklister) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
        this.accessTokenBlacklister = accessTokenBlacklister;
    }
```

(d) `withdraw` 메서드 추가(`updateMe` 근처, public 메서드 영역):
```java
    @Transactional
    public void withdraw(Long memberId, String uuid, String accessToken, String rawPassword) {
        Member member = findActive(memberId);
        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED);
        }
        member.withdraw();
        persist(member);
        refreshTokenStore.revokeAll(uuid);
        accessTokenBlacklister.blacklist(accessToken);
    }
```

- [ ] **Step 5: 컨트롤러 구현** — `MeController.java`

(a) import 추가:
```java
import com.elipair.church.domain.member.dto.WithdrawRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
```

(b) `updateMe` 핸들러 다음에 추가:
```java
    @Operation(
            summary = "회원 탈퇴",
            description =
                    """
                    본인 계정 자가탈퇴. 소프트 삭제 + 개인정보 스크럽 후 전체 세션을 무효화한다.

                    - 인증(JWT): 필요 — 로그인(본인)
                    - 요청 본문: `WithdrawRequest` — 현재 비밀번호(재인증)
                    - 반환값: 없음(204)
                    - 부수효과: 비밀번호 불일치 401 · 모든 리프레시 토큰 회수 + 현재 access 블랙리스트
                    """)
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody WithdrawRequest request) {
        String accessToken = (authorization != null && authorization.startsWith("Bearer "))
                ? authorization.substring(7)
                : authorization;
        service.withdraw(principal.id(), principal.uuid(), accessToken, request.password());
    }
```

- [ ] **Step 6: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.WithdrawApiTest'`
Expected: PASS (3 tests)

- [ ] **Step 7: 포맷 + 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/domain/member/dto/WithdrawRequest.java \
        src/main/java/com/elipair/church/domain/member/controller/MeController.java \
        src/main/java/com/elipair/church/domain/member/MemberService.java \
        src/test/java/com/elipair/church/domain/member/WithdrawApiTest.java
git commit -m "feat : 자가 회원탈퇴 API(DELETE /api/members/me) #38"
```

---

### Task 4: 마지막 SUPER_ADMIN 자가탈퇴 차단

마지막 활성 SUPER_ADMIN이 탈퇴하면 시스템에 최고관리자가 사라지므로 차단(403). 기존 `RoleHierarchyValidator` 재사용.

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/member/MemberService.java`
- Modify: `src/test/java/com/elipair/church/domain/member/WithdrawApiTest.java`

- [ ] **Step 1: 실패 테스트 추가** — `WithdrawApiTest.java`에 메서드 추가

```java
    @Test
    void last_super_admin_cannot_withdraw() throws Exception {
        persistMember("01099990000", "password123", "SUPER_ADMIN");
        String[] t = login("01099990000", "password123");

        mockMvc.perform(delete("/api/members/me")
                        .header("Authorization", "Bearer " + t[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.WithdrawApiTest.last_super_admin_cannot_withdraw'`
Expected: FAIL — 가드가 없어 204가 떨어짐(기대 403).

- [ ] **Step 3: 가드 구현** — `MemberService.java`

(a) import 추가:
```java
import com.elipair.church.global.security.RoleHierarchyValidator;
```

(b) 상수 추가(클래스 상단):
```java
    private static final String SUPER_ADMIN = "SUPER_ADMIN";
```

(c) 필드 추가:
```java
    private final RoleHierarchyValidator hierarchyValidator;
```

(d) 생성자에 파라미터·할당 추가(전체 교체):
```java
    public MemberService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenStore refreshTokenStore,
            AccessTokenBlacklister accessTokenBlacklister,
            RoleHierarchyValidator hierarchyValidator) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenStore = refreshTokenStore;
        this.accessTokenBlacklister = accessTokenBlacklister;
        this.hierarchyValidator = hierarchyValidator;
    }
```

(e) `withdraw`의 비밀번호 검증 직후, `member.withdraw()` 호출 전에 가드 삽입:
```java
        if (member.hasRole(SUPER_ADMIN)) {
            long activeSuperAdmins = memberRepository.countByRoles_NameAndDeletedAtIsNull(SUPER_ADMIN);
            hierarchyValidator.validateNotLastSuperAdmin(true, activeSuperAdmins);
        }
```
삽입 후 `withdraw` 본문은 다음 순서가 된다: `findActive` → 비밀번호 `matches` 검증 → SUPER_ADMIN 가드 → `member.withdraw()` → `persist` → `revokeAll` → `blacklist`.

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.WithdrawApiTest'`
Expected: PASS (4 tests — 기존 3 + 마지막 SUPER_ADMIN 403)

- [ ] **Step 5: 포맷 + 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/domain/member/MemberService.java \
        src/test/java/com/elipair/church/domain/member/WithdrawApiTest.java
git commit -m "feat : 마지막 SUPER_ADMIN 자가탈퇴 차단 #38"
```

---

### Task 5: 스펙 반영 + 전체 검증

`docs/church-backend-spec.md` §5.2에 엔드포인트·정책을 반영하고 전체 빌드를 green으로 마무리.

**Files:**
- Modify: `docs/church-backend-spec.md`

- [ ] **Step 1: §5.2 표에 행 추가** — `docs/church-backend-spec.md`

§5.2 회원 표에서 `PATCH | /api/members/me | 인증 | 내 정보 수정 ...` 행 바로 아래에 추가:
```
| DELETE | /api/members/me | 인증 | 회원 탈퇴(자가탈퇴). 비밀번호 재인증 후 soft delete + 개인정보 스크럽 + 전체 세션 무효화 |
```

- [ ] **Step 2: 정책 주석 추가** — 같은 §5.2 섹션 하단(약관 재동의 사이클 설명 앞 적절한 위치)에 추가:
```
회원 탈퇴(자가탈퇴) 정책:
- `DELETE /api/members/me` — 본인만. 요청 본문에 현재 비밀번호를 받아 재인증(불일치 401 AUTHENTICATION_FAILED).
- 물리 삭제가 아니라 soft delete(`deleted_at`) + 개인정보 스크럽: phone·name은 비식별 토큰값, email은 null, password는 사용불가 값. 작성 콘텐츠는 FK 유지 + `(탈퇴한 사용자)` 표시.
- 탈퇴 즉시 전체 세션 무효화: 모든 리프레시 토큰 회수 + 현재 access 토큰 블랙리스트.
- 마지막 활성 SUPER_ADMIN은 탈퇴 차단(403 ACCESS_DENIED).
- 재가입: 탈퇴한 전화번호로 재가입 가능(부분 유니크가 활성 회원만 대상). 단 새 계정(새 uuid)이며 이전 데이터와 무관.
```

- [ ] **Step 3: 전체 빌드 검증**

Run: `./gradlew spotlessApply && ./gradlew build`
Expected: BUILD SUCCESSFUL — 전체 테스트 green(기존 회귀 포함).

- [ ] **Step 4: 커밋**

```bash
git add docs/church-backend-spec.md
git commit -m "docs : 회원탈퇴 엔드포인트·정책 스펙 반영 #38"
```

---

## 참고: 손대지 않는 것

- 마이그레이션 없음(스키마 무변경).
- `version.yml`·`build.gradle` version·`CHANGELOG.*`·`.github/**`는 automation 소유 — 건드리지 않음.
- 미추적 dev 시드 파일(`application-dev.yml`, `db/dev/`)은 이번 작업과 무관 — `git add`에 포함하지 말 것.
- 관리자 강제탈퇴, 탈퇴 복구, 유예기간 등은 비범위(YAGNI).
