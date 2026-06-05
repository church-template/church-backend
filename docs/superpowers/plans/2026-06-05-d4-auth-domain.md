# D4 인증(Auth) 도메인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/api/auth`의 signup·login·refresh·logout 4개 엔드포인트를 `domain/auth` 패키지에 구현해 인증 흐름을 완성한다.

**Architecture:** G3(JWT 발급/검증·Redis refresh·blacklist)와 D3(member 엔티티·`Member.create`·권한 플래트닝·phone 정규화)가 만든 인프라를 단일 `AuthService`가 오케스트레이션한다. 신규 마이그레이션·SecurityConfig·ErrorCode 변경 없음. `domain/member`의 `PhoneNumbers`(public 승격)·`MemberRepository.findByPhone`(@EntityGraph)만 소폭 보정.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Security, jjwt 0.12.6, PostgreSQL/Redis(Testcontainers), JUnit5 + Mockito + AssertJ.

**설계 근거:** `docs/superpowers/specs/2026-06-05-d4-auth-domain-design.md`

**시작 전 (실행 시):** feature 브랜치 `20260605_#9_인증_도메인`에서 작업한다(워크트리는 using-git-worktrees로 준비). 각 Task는 독립 커밋. 커밋 메시지 컨벤션: `feat : <설명> #9`(콜론 앞 공백, 한글).

**확정된 계약 결정(설계 리뷰 반영):**
- refresh = **Access만 재발급**, 기존 refreshToken echo. logout = **현재 기기만**(access blacklist + 해당 refresh revoke). signup = **201 + 토큰 없는 요약**.
- `parse` 실패는 반드시 `try/catch(JwtException | IllegalArgumentException)` → refresh는 `INVALID_TOKEN`, logout은 skip(안 하면 500).
- `member.phone`은 정규화값(숫자만) 반환. `SignupResponse`는 position 필드 없음.

---

## File Structure

**신규 (`src/main/java/com/elipair/church/domain/auth/`)**
- `controller/AuthController.java` — `/api/auth` 4개 엔드포인트
- `AuthService.java` — 4개 동작 오케스트레이션 + private 헬퍼
- `dto/SignupRequest.java` · `dto/SignupResponse.java`
- `dto/LoginRequest.java` · `dto/LoginResponse.java`
- `dto/RefreshRequest.java` · `dto/RefreshResponse.java`
- `dto/LogoutRequest.java`
- `dto/TokenPair.java` · `dto/MemberSummary.java`(login 전용)

**신규 테스트 (`src/test/java/com/elipair/church/domain/auth/`)**
- `AuthServiceTest.java` — Mockito 단위(열거 방지·signup·refresh·logout 분기)
- `AuthApiTest.java` — `@SpringBootTest` 통합(4개 엔드포인트 E2E)

**수정 (`domain/member`)**
- `PhoneNumbers.java` — class·`normalize` public 승격
- `MemberRepository.java` — `findByPhoneAndDeletedAtIsNull`에 `@EntityGraph` 추가

---

## Task 1: `domain/member` 가시성·페치 보정 (enabling)

`domain.auth`가 `PhoneNumbers.normalize`를 호출할 수 있게 public화하고, login 경로 N+1·lazy를 막기 위해 `findByPhone`에 `@EntityGraph`를 단다. 동작 변경이 아닌 enabling 리팩터 — 기존 테스트가 녹색이면 성공.

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/member/PhoneNumbers.java:7,11`
- Modify: `src/main/java/com/elipair/church/domain/member/MemberRepository.java:20`

- [ ] **Step 1: `PhoneNumbers`를 public으로 승격**

`PhoneNumbers.java`에서 클래스 선언과 `normalize` 시그니처를 변경한다(주석은 이미 "D4 로그인도 재사용"을 명시).

```java
/** 전화번호를 숫자만 남겨 정규화한다(스펙 §3 "digits-only normalized"). D4 로그인도 재사용. */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    public static String normalize(String raw) {
```

(본문 로직은 그대로. `private PhoneNumbers()` 생성자는 변경 없음.)

- [ ] **Step 2: `findByPhoneAndDeletedAtIsNull`에 `@EntityGraph` 추가**

`MemberRepository.java`의 해당 줄을 형제 메서드와 동일한 페치 그래프로 바꾼다.

```java
    @EntityGraph(attributePaths = {"position", "roles", "roles.permissions"})
    Optional<Member> findByPhoneAndDeletedAtIsNull(String phone); // D4 로그인 재사용
```

(`@EntityGraph`는 이미 같은 파일에서 import됨 — 추가 import 불필요.)

- [ ] **Step 3: 기존 member 테스트가 여전히 녹색인지 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.member.*'`
Expected: PASS (PhoneNumbersTest·MemberRepositoryTest·MeApiTest 등 전부 통과 — 시그니처 호환 변경이라 깨지지 않음)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/elipair/church/domain/member/PhoneNumbers.java \
        src/main/java/com/elipair/church/domain/member/MemberRepository.java
git commit -m "refactor : PhoneNumbers public 승격·findByPhone EntityGraph 추가(D4 준비) #9"
```

---

## Task 2: Auth DTO 8종

요청/응답 레코드를 만든다. Bean Validation 애너테이션의 동작은 이후 API 테스트(Task 4·6·8·10)가 검증한다. 여기서는 컴파일만 확인.

**Files:**
- Create: `src/main/java/com/elipair/church/domain/auth/dto/SignupRequest.java`
- Create: `src/main/java/com/elipair/church/domain/auth/dto/SignupResponse.java`
- Create: `src/main/java/com/elipair/church/domain/auth/dto/LoginRequest.java`
- Create: `src/main/java/com/elipair/church/domain/auth/dto/TokenPair.java`
- Create: `src/main/java/com/elipair/church/domain/auth/dto/MemberSummary.java`
- Create: `src/main/java/com/elipair/church/domain/auth/dto/LoginResponse.java`
- Create: `src/main/java/com/elipair/church/domain/auth/dto/RefreshRequest.java`
- Create: `src/main/java/com/elipair/church/domain/auth/dto/RefreshResponse.java`
- Create: `src/main/java/com/elipair/church/domain/auth/dto/LogoutRequest.java`

- [ ] **Step 1: 요청 DTO 작성**

`SignupRequest.java`:
```java
package com.elipair.church.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank String phone,
        @NotBlank String name,
        @NotBlank @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다") String password,
        @Email String email,
        @AssertTrue(message = "이용약관에 동의해야 합니다") boolean termsAgreed,
        @AssertTrue(message = "개인정보 수집·이용에 동의해야 합니다") boolean privacyAgreed) {}
```

`LoginRequest.java`:
```java
package com.elipair.church.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String phone, @NotBlank String password) {}
```

`RefreshRequest.java`:
```java
package com.elipair.church.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {}
```

`LogoutRequest.java`:
```java
package com.elipair.church.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank String refreshToken) {}
```

- [ ] **Step 2: 응답 DTO 작성**

`TokenPair.java`:
```java
package com.elipair.church.domain.auth.dto;

public record TokenPair(String accessToken, String refreshToken) {}
```

`MemberSummary.java` (login 응답 전용 — position 포함):
```java
package com.elipair.church.domain.auth.dto;

import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.role.Role;
import java.util.List;

public record MemberSummary(String uuid, String name, String phone, String position, List<String> roles) {

    public static MemberSummary from(Member m) {
        return new MemberSummary(
                m.getUuid().toString(),
                m.getName(),
                m.getPhone(),
                m.getPosition() == null ? null : m.getPosition().getName(),
                m.getRoles().stream().map(Role::getName).sorted().toList());
    }
}
```

`SignupResponse.java` (position 없음 — 가입 시 항상 null이라 제외):
```java
package com.elipair.church.domain.auth.dto;

import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.role.Role;
import java.util.List;

public record SignupResponse(String uuid, String name, String phone, List<String> roles) {

    public static SignupResponse from(Member m) {
        return new SignupResponse(
                m.getUuid().toString(),
                m.getName(),
                m.getPhone(),
                m.getRoles().stream().map(Role::getName).sorted().toList());
    }
}
```

`LoginResponse.java`:
```java
package com.elipair.church.domain.auth.dto;

public record LoginResponse(TokenPair tokens, MemberSummary member, boolean requiresAgreement) {}
```

`RefreshResponse.java`:
```java
package com.elipair.church.domain.auth.dto;

public record RefreshResponse(TokenPair tokens) {}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/elipair/church/domain/auth/dto/
git commit -m "feat : auth 요청·응답 DTO 추가 #9"
```

---

## Task 3: `AuthService` 골격 + signup (TDD)

`AuthService`를 의존성과 함께 만들고 `signup`을 구현한다. login/refresh/logout은 이후 Task에서 추가한다.

**Files:**
- Create: `src/main/java/com/elipair/church/domain/auth/AuthService.java`
- Test: `src/test/java/com/elipair/church/domain/auth/AuthServiceTest.java`

- [ ] **Step 1: 실패하는 signup 단위 테스트 작성**

`AuthServiceTest.java`:
```java
package com.elipair.church.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.auth.dto.SignupRequest;
import com.elipair.church.domain.auth.dto.SignupResponse;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.redis.RefreshTokenStore;
import com.elipair.church.global.security.redis.TokenBlacklist;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private TokenBlacklist tokenBlacklist;

    @InjectMocks private AuthService authService;

    @Test
    void signup_normalizes_phone_grants_user_and_returns_summary() {
        when(memberRepository.existsByPhoneAndDeletedAtIsNull("01012345678")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("{bcrypt}");
        Role userRole = mock(Role.class);
        when(userRole.getName()).thenReturn("USER");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        SignupResponse res = authService.signup(
                new SignupRequest("010-1234-5678", "홍길동", "password123", null, true, true));

        assertThat(res.name()).isEqualTo("홍길동");
        assertThat(res.phone()).isEqualTo("01012345678");
        assertThat(res.roles()).containsExactly("USER");
    }

    @Test
    void signup_duplicate_phone_is_duplicate_resource() {
        when(memberRepository.existsByPhoneAndDeletedAtIsNull("01012345678")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(
                        new SignupRequest("010-1234-5678", "홍길동", "password123", null, true, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }
}
```

- [ ] **Step 2: 테스트가 컴파일 실패(=red)하는지 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthServiceTest'`
Expected: 컴파일 실패 — `AuthService` 클래스 없음

- [ ] **Step 3: `AuthService` 골격 + signup 구현**

`AuthService.java`:
```java
package com.elipair.church.domain.auth;

import com.elipair.church.domain.auth.dto.SignupRequest;
import com.elipair.church.domain.auth.dto.SignupResponse;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.member.PhoneNumbers;
import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.redis.RefreshTokenStore;
import com.elipair.church.global.security.redis.TokenBlacklist;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final MemberRepository memberRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklist tokenBlacklist;

    public AuthService(
            MemberRepository memberRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            RefreshTokenStore refreshTokenStore,
            TokenBlacklist tokenBlacklist) {
        this.memberRepository = memberRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        if (memberRepository.existsByPhoneAndDeletedAtIsNull(phone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        Member member = Member.create(
                phone,
                request.name(),
                passwordEncoder.encode(request.password()),
                request.email(),
                null,
                request.termsAgreed(),
                request.privacyAgreed());
        Role userRole = roleRepository
                .findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("USER 역할 시드(V2)가 없습니다"));
        member.grantRole(userRole);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE); // 동시 가입 경합 백스톱(partial unique)
        }
        return SignupResponse.from(member);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인(green)**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthServiceTest'`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/elipair/church/domain/auth/AuthService.java \
        src/test/java/com/elipair/church/domain/auth/AuthServiceTest.java
git commit -m "feat : AuthService signup(USER 자동부여·중복 409) 추가 #9"
```

---

## Task 4: signup 엔드포인트 + 통합 테스트

`AuthController`를 만들어 signup을 노출하고, Testcontainers 통합 테스트로 201·검증 실패를 확인한다.

**Files:**
- Create: `src/main/java/com/elipair/church/domain/auth/controller/AuthController.java`
- Test: `src/test/java/com/elipair/church/domain/auth/AuthApiTest.java`

- [ ] **Step 1: 실패하는 signup API 테스트 작성**

`AuthApiTest.java`:
```java
package com.elipair.church.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository memberRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

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

    @Test
    void signup_creates_member_with_user_role() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"name\":\"홍길동\",\"password\":\"password123\","
                                + "\"termsAgreed\":true,\"privacyAgreed\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").isNotEmpty())
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void signup_duplicate_phone_is_409() throws Exception {
        Member existing = Member.create("01012345678", "기존", "{enc}", null, null, true, true);
        existing.grantRole(role("USER"));
        memberRepository.saveAndFlush(existing);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"name\":\"새사람\",\"password\":\"password123\","
                                + "\"termsAgreed\":true,\"privacyAgreed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void signup_without_consent_is_400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"name\":\"홍길동\",\"password\":\"password123\","
                                + "\"termsAgreed\":false,\"privacyAgreed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void signup_short_password_is_400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"name\":\"홍길동\",\"password\":\"short\","
                                + "\"termsAgreed\":true,\"privacyAgreed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }
}
```

(`PasswordEncoder`·`ObjectMapper`는 이후 Task의 login/refresh 테스트에서 쓰이므로 미리 autowire — 이 Task의 signup 테스트만으로는 unused이나 곧 사용된다.)

- [ ] **Step 2: 테스트 red 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthApiTest'`
Expected: 컴파일 실패 또는 404 — `AuthController` 없음

- [ ] **Step 3: `AuthController` + signup 구현**

`AuthController.java`:
```java
package com.elipair.church.domain.auth.controller;

import com.elipair.church.domain.auth.AuthService;
import com.elipair.church.domain.auth.dto.SignupRequest;
import com.elipair.church.domain.auth.dto.SignupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증(스펙 §5.1). signup·login·refresh는 공개, logout만 메서드 보안으로 인증 강제. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }
}
```

- [ ] **Step 4: 테스트 green 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthApiTest'`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/elipair/church/domain/auth/controller/AuthController.java \
        src/test/java/com/elipair/church/domain/auth/AuthApiTest.java
git commit -m "feat : POST /api/auth/signup 엔드포인트 추가 #9"
```

---

## Task 5: `AuthService.login` (TDD)

phone+password 인증, 실패 동일 401, 토큰 발급. login 성공 round-trip은 Task 6 통합 테스트가 검증하고, 여기서는 **열거 방지(미존재·불일치 동일 예외)** 단위 테스트에 집중한다.

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/auth/AuthService.java`
- Test: `src/test/java/com/elipair/church/domain/auth/AuthServiceTest.java`

- [ ] **Step 1: 실패하는 login 단위 테스트 추가**

`AuthServiceTest.java`에 메서드 추가(import 추가: `com.elipair.church.domain.auth.dto.LoginRequest`):
```java
    @Test
    void login_unknown_phone_is_authentication_failed() {
        when(memberRepository.findByPhoneAndDeletedAtIsNull("01012345678")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("010-1234-5678", "pw")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTHENTICATION_FAILED);
    }

    @Test
    void login_wrong_password_is_authentication_failed() {
        Member m = Member.create("01012345678", "홍길동", "{enc}", null, null, true, true);
        when(memberRepository.findByPhoneAndDeletedAtIsNull("01012345678")).thenReturn(Optional.of(m));
        when(passwordEncoder.matches("pw", "{enc}")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("010-1234-5678", "pw")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTHENTICATION_FAILED); // 미존재와 동일 코드 — 열거 방지
    }
```

- [ ] **Step 2: red 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthServiceTest'`
Expected: 컴파일 실패 — `login` 메서드 없음

- [ ] **Step 3: login + 토큰 헬퍼 구현**

`AuthService.java`에 import 추가:
```java
import com.elipair.church.domain.auth.dto.LoginRequest;
import com.elipair.church.domain.auth.dto.LoginResponse;
import com.elipair.church.domain.auth.dto.MemberSummary;
import com.elipair.church.domain.auth.dto.TokenPair;
import com.elipair.church.domain.member.MemberAuthorities;
import com.elipair.church.global.security.MemberPrincipal;
import io.jsonwebtoken.Claims;
```

메서드 추가(클래스 내부, `signup` 아래):
```java
    public LoginResponse login(LoginRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        Member member = memberRepository
                .findByPhoneAndDeletedAtIsNull(phone)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_FAILED));
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_FAILED); // 미존재와 구분 없는 동일 응답
        }
        TokenPair tokens = issueTokens(member);
        boolean requiresAgreement = !(member.isTermsAgreed() && member.isPrivacyAgreed());
        return new LoginResponse(tokens, MemberSummary.from(member), requiresAgreement);
    }

    /** access + refresh 발급, 발급한 refresh를 재파싱해 jti·exp로 다중세션 등록. */
    private TokenPair issueTokens(Member member) {
        String access = tokenProvider.issueAccess(principalOf(member), positionOf(member),
                MemberAuthorities.permissions(member));
        String refresh = tokenProvider.issueRefresh(member.getUuid().toString());
        Claims claims = tokenProvider.parse(refresh); // 자기 서명 토큰 — 항상 성공
        refreshTokenStore.save(member.getUuid().toString(), claims.getId(), claims.getExpiration().toInstant());
        return new TokenPair(access, refresh);
    }

    private MemberPrincipal principalOf(Member m) {
        return new MemberPrincipal(m.getId(), m.getUuid().toString(), m.getName(), MemberAuthorities.maxPriority(m));
    }

    private String positionOf(Member m) {
        return m.getPosition() == null ? null : m.getPosition().getName();
    }
```

- [ ] **Step 4: green 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthServiceTest'`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/elipair/church/domain/auth/AuthService.java \
        src/test/java/com/elipair/church/domain/auth/AuthServiceTest.java
git commit -m "feat : AuthService login(열거 방지·토큰 발급) 추가 #9"
```

---

## Task 6: login 엔드포인트 + 통합 테스트

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/auth/controller/AuthController.java`
- Test: `src/test/java/com/elipair/church/domain/auth/AuthApiTest.java`

- [ ] **Step 1: 실패하는 login API 테스트 추가**

`AuthApiTest.java`에 헬퍼 + 테스트 추가. 새 import:
```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.elipair.church.global.security.JwtTokenProvider;
```
`JwtTokenProvider`는 이후 Task에서 토큰 파싱에 쓰이므로 필드도 추가:
```java
    @Autowired private JwtTokenProvider provider;
```
회원을 BCrypt 비번으로 만드는 헬퍼 + 테스트:
```java
    /** BCrypt 비번으로 활성 회원 생성 후 저장. roleName이 null이면 역할 없음. */
    private Member persistMember(String phone, String rawPassword, String roleName) {
        Member m = Member.create(phone, "홍길동", passwordEncoder.encode(rawPassword), null, null, true, true);
        if (roleName != null) {
            m.grantRole(role(roleName));
        }
        return memberRepository.saveAndFlush(m);
    }

    @Test
    void login_success_returns_tokens_and_member() throws Exception {
        persistMember("01012345678", "password123", "USER");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokens.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokens.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.member.phone").value("01012345678"))
                .andExpect(jsonPath("$.member.roles[0]").value("USER"))
                .andExpect(jsonPath("$.requiresAgreement").value(false));
    }

    @Test
    void login_wrong_password_is_401() throws Exception {
        persistMember("01012345678", "password123", "USER");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void login_unknown_phone_is_same_401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-0000-0000\",\"password\":\"password123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void login_with_reset_agreement_requires_agreement() throws Exception {
        Member m = Member.create("01012345678", "홍길동", passwordEncoder.encode("password123"), null, null, true, true);
        m.grantRole(role("USER"));
        m.resetAgreement("terms"); // 약관 개정 시뮬레이션 → termsAgreed=false
        memberRepository.saveAndFlush(m);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresAgreement").value(true));
    }
```

- [ ] **Step 2: red 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthApiTest'`
Expected: login 테스트 실패(404 — login 매핑 없음)

- [ ] **Step 3: login 엔드포인트 추가**

`AuthController.java`에 import 추가:
```java
import com.elipair.church.domain.auth.dto.LoginRequest;
import com.elipair.church.domain.auth.dto.LoginResponse;
```
메서드 추가:
```java
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
```

- [ ] **Step 4: green 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthApiTest'`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/elipair/church/domain/auth/controller/AuthController.java \
        src/test/java/com/elipair/church/domain/auth/AuthApiTest.java
git commit -m "feat : POST /api/auth/login 엔드포인트 추가 #9"
```

---

## Task 7: `AuthService.refresh` (TDD)

refresh 토큰 검증 → **Access만 재발급**(기존 refresh echo). parse 실패·type 오류·revoke를 전부 `INVALID_TOKEN`으로.

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/auth/AuthService.java`
- Test: `src/test/java/com/elipair/church/domain/auth/AuthServiceTest.java`

- [ ] **Step 1: 실패하는 refresh 단위 테스트 추가**

`AuthServiceTest.java`에 import 추가:
```java
import com.elipair.church.domain.auth.dto.RefreshRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
```
테스트 추가:
```java
    @Test
    void refresh_parse_failure_is_invalid_token() {
        when(tokenProvider.parse("bad")).thenThrow(new MalformedJwtException("bad"));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("bad")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN); // 500이 아니라 401
    }

    @Test
    void refresh_access_type_token_is_invalid_token() {
        Claims claims = mock(Claims.class);
        when(tokenProvider.parse("tok")).thenReturn(claims);
        when(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class)).thenReturn("access");

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("tok")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void refresh_revoked_token_is_invalid_token() {
        Claims claims = mock(Claims.class);
        when(tokenProvider.parse("tok")).thenReturn(claims);
        when(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class)).thenReturn("refresh");
        when(claims.getSubject()).thenReturn("uuid-1");
        when(claims.getId()).thenReturn("jti-1");
        when(refreshTokenStore.isValid("uuid-1", "jti-1")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("tok")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }
```

- [ ] **Step 2: red 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthServiceTest'`
Expected: 컴파일 실패 — `refresh` 메서드 없음

- [ ] **Step 3: refresh + parseToken 헬퍼 구현**

`AuthService.java`에 import 추가:
```java
import com.elipair.church.domain.auth.dto.RefreshRequest;
import com.elipair.church.domain.auth.dto.RefreshResponse;
import java.util.UUID;
import io.jsonwebtoken.JwtException;
```
메서드 추가:
```java
    public RefreshResponse refresh(RefreshRequest request) {
        Claims claims = parseToken(request.refreshToken());
        if (!JwtTokenProvider.TYPE_REFRESH.equals(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        String uuid = claims.getSubject();
        String jti = claims.getId();
        if (jti == null || !refreshTokenStore.isValid(uuid, jti)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN); // revoke·만료·미등록
        }
        Member member = memberRepository
                .findByUuidAndDeletedAtIsNull(UUID.fromString(uuid))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN)); // 탈퇴 회원
        // Access만 재발급 — DB에서 권한 재조회(스펙 §4.1), refresh는 그대로 echo
        String access = tokenProvider.issueAccess(principalOf(member), positionOf(member),
                MemberAuthorities.permissions(member));
        return new RefreshResponse(new TokenPair(access, request.refreshToken()));
    }

    /** 토큰 파싱 실패(만료·위변조·형식)를 INVALID_TOKEN으로 변환. 핸들러에 JwtException 핸들러가 없어 미변환 시 500. */
    private Claims parseToken(String token) {
        try {
            return tokenProvider.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }
```

- [ ] **Step 4: green 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthServiceTest'`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/elipair/church/domain/auth/AuthService.java \
        src/test/java/com/elipair/church/domain/auth/AuthServiceTest.java
git commit -m "feat : AuthService refresh(Access만 재발급·권한 재조회) 추가 #9"
```

---

## Task 8: refresh 엔드포인트 + 통합 테스트

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/auth/controller/AuthController.java`
- Test: `src/test/java/com/elipair/church/domain/auth/AuthApiTest.java`

- [ ] **Step 1: 실패하는 refresh API 테스트 추가**

`AuthApiTest.java`에 import 추가:
```java
import com.elipair.church.global.security.MemberPrincipal;
import io.jsonwebtoken.Claims;
import java.util.List;
```
응답 본문에서 토큰을 뽑는 헬퍼 + 테스트:
```java
    private String loginAndReadToken(String phone, String rawPassword, String field) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + rawPassword + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("tokens").path(field).asText();
    }

    @Test
    void refresh_reissues_access_and_reflects_new_permission() throws Exception {
        Member m = persistMember("01012345678", "password123", "USER"); // USER = 권한 없음
        String refresh = loginAndReadToken("010-1234-5678", "password123", "refreshToken");

        // login 이후 MEMBER 역할 부여 → GALLERY_VIEW 생김
        Member loaded = memberRepository.findById(m.getId()).orElseThrow();
        loaded.grantRole(role("MEMBER"));
        memberRepository.saveAndFlush(loaded);

        String body = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokens.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokens.refreshToken").value(refresh)) // refresh echo
                .andReturn().getResponse().getContentAsString();

        String newAccess = objectMapper.readTree(body).path("tokens").path("accessToken").asText();
        Claims claims = provider.parse(newAccess);
        @SuppressWarnings("unchecked")
        List<String> permissions = claims.get(JwtTokenProvider.CLAIM_PERMISSIONS, List.class);
        org.assertj.core.api.Assertions.assertThat(permissions).contains("GALLERY_VIEW");
    }

    @Test
    void refresh_invalid_token_is_401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-jwt\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void refresh_with_access_token_is_401() throws Exception {
        persistMember("01012345678", "password123", "USER");
        String access = loginAndReadToken("010-1234-5678", "password123", "accessToken");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + access + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN")); // type=access 거부
    }
```

- [ ] **Step 2: red 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthApiTest'`
Expected: refresh 테스트 실패(404)

- [ ] **Step 3: refresh 엔드포인트 추가**

`AuthController.java`에 import 추가:
```java
import com.elipair.church.domain.auth.dto.RefreshRequest;
import com.elipair.church.domain.auth.dto.RefreshResponse;
```
메서드 추가:
```java
    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }
```

- [ ] **Step 4: green 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthApiTest'`
Expected: PASS (11 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/elipair/church/domain/auth/controller/AuthController.java \
        src/test/java/com/elipair/church/domain/auth/AuthApiTest.java
git commit -m "feat : POST /api/auth/refresh 엔드포인트 추가 #9"
```

---

## Task 9: `AuthService.logout` (TDD)

현재 기기 로그아웃: access jti 블랙리스트 + 본인 소유 refresh만 revoke. 무효/타인 refresh는 skip(멱등).

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/auth/AuthService.java`
- Test: `src/test/java/com/elipair/church/domain/auth/AuthServiceTest.java`

- [ ] **Step 1: 실패하는 logout 단위 테스트 추가**

`AuthServiceTest.java`에 import 추가:
```java
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.elipair.church.global.security.MemberPrincipal;
import java.util.Date;
```
테스트 추가:
```java
    @Test
    void logout_blacklists_access_and_revokes_owned_refresh() {
        Claims access = mock(Claims.class);
        when(tokenProvider.parse("access-tok")).thenReturn(access);
        when(access.getId()).thenReturn("ajti");
        when(access.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        Claims refresh = mock(Claims.class);
        when(tokenProvider.parse("refresh-tok")).thenReturn(refresh);
        when(refresh.get(JwtTokenProvider.CLAIM_TYPE, String.class)).thenReturn("refresh");
        when(refresh.getSubject()).thenReturn("uuid-1");
        when(refresh.getId()).thenReturn("rjti");

        authService.logout(new MemberPrincipal(1L, "uuid-1", "n", 100), "access-tok", "refresh-tok");

        verify(tokenBlacklist).blacklist(eq("ajti"), any());
        verify(refreshTokenStore).revoke("uuid-1", "rjti");
    }

    @Test
    void logout_skips_revoke_for_other_users_refresh() {
        Claims access = mock(Claims.class);
        when(tokenProvider.parse("access-tok")).thenReturn(access);
        when(access.getId()).thenReturn("ajti");
        when(access.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        Claims refresh = mock(Claims.class);
        when(tokenProvider.parse("other-tok")).thenReturn(refresh);
        when(refresh.get(JwtTokenProvider.CLAIM_TYPE, String.class)).thenReturn("refresh");
        when(refresh.getSubject()).thenReturn("uuid-OTHER");

        authService.logout(new MemberPrincipal(1L, "uuid-1", "n", 100), "access-tok", "other-tok");

        verify(tokenBlacklist).blacklist(eq("ajti"), any());
        verify(refreshTokenStore, never()).revoke(any(), any());
    }
```

- [ ] **Step 2: red 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthServiceTest'`
Expected: 컴파일 실패 — `logout` 메서드 없음

- [ ] **Step 3: logout + 헬퍼 구현**

`AuthService.java`에 메서드 추가(이미 `Claims`·`JwtException`·`MemberPrincipal` import됨):
```java
    public void logout(MemberPrincipal principal, String accessToken, String refreshToken) {
        blacklistAccess(accessToken);
        revokeRefreshIfOwned(principal.uuid(), refreshToken);
    }

    /** 현재 access 토큰을 jti·남은 수명으로 블랙리스트. 필터가 이미 검증했으므로 방어적 skip만. */
    private void blacklistAccess(String accessToken) {
        try {
            Claims claims = tokenProvider.parse(accessToken);
            if (claims.getId() != null) {
                tokenBlacklist.blacklist(claims.getId(), claims.getExpiration().toInstant());
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // 도달 드묾(필터 통과 토큰) — 방어적 무시
        }
    }

    /** 본인 소유 refresh일 때만 revoke. 무효/타인 토큰은 skip(멱등 로그아웃 — INVALID_TOKEN 던지지 않음). */
    private void revokeRefreshIfOwned(String requesterUuid, String refreshToken) {
        try {
            Claims claims = tokenProvider.parse(refreshToken);
            boolean isRefresh =
                    JwtTokenProvider.TYPE_REFRESH.equals(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class));
            if (isRefresh && requesterUuid.equals(claims.getSubject()) && claims.getId() != null) {
                refreshTokenStore.revoke(requesterUuid, claims.getId());
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // 무효 refresh — skip
        }
    }
```

- [ ] **Step 4: green 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthServiceTest'`
Expected: PASS (9 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/elipair/church/domain/auth/AuthService.java \
        src/test/java/com/elipair/church/domain/auth/AuthServiceTest.java
git commit -m "feat : AuthService logout(현재 기기 블랙리스트·refresh revoke) 추가 #9"
```

---

## Task 10: logout 엔드포인트 + 통합 테스트(E2E)

logout은 메서드 보안으로 인증 강제. Authorization 헤더는 `required = false`로 받아 **무인증 시 검증오류(400)가 아닌 메서드보안 401**이 나게 한다.

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/auth/controller/AuthController.java`
- Test: `src/test/java/com/elipair/church/domain/auth/AuthApiTest.java`

- [ ] **Step 1: 실패하는 logout API 테스트 추가**

`AuthApiTest.java`에 테스트 추가(import 추가 없음 — 기존 것 재사용):
```java
    @Test
    void logout_blacklists_access_and_revokes_refresh() throws Exception {
        persistMember("01012345678", "password123", "USER");
        String access = loginAndReadToken("010-1234-5678", "password123", "accessToken");
        String refresh = loginAndReadToken("010-1234-5678", "password123", "refreshToken");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());

        // (a) 블랙리스트된 access로 보호 경로 접근 → 401
        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());

        // (b) revoke된 refresh로 재발급 시도 → 401
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void logout_without_authentication_is_401() throws Exception {
        // 유효한 본문이지만 인증 없음 → @PreAuthorize 거부(검증오류 아님)
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"some-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }
```

(주의: 위 `loginAndReadToken`을 두 번 호출하면 refresh 세션이 2개 생긴다 — 테스트 의도상 무방하나, access/refresh를 한 번의 login에서 함께 뽑고 싶으면 본문을 한 번만 읽어 두 필드를 추출해도 된다.)

- [ ] **Step 2: red 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthApiTest'`
Expected: logout 테스트 실패(404)

- [ ] **Step 3: logout 엔드포인트 추가**

`AuthController.java`에 import 추가:
```java
import com.elipair.church.domain.auth.dto.LogoutRequest;
import com.elipair.church.global.security.MemberPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestHeader;
```
메서드 추가:
```java
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void logout(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody LogoutRequest request) {
        String accessToken = (authorization != null && authorization.startsWith("Bearer "))
                ? authorization.substring(7)
                : authorization;
        authService.logout(principal, accessToken, request.refreshToken());
    }
```

- [ ] **Step 4: green 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.AuthApiTest'`
Expected: PASS (13 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/elipair/church/domain/auth/controller/AuthController.java \
        src/test/java/com/elipair/church/domain/auth/AuthApiTest.java
git commit -m "feat : POST /api/auth/logout 엔드포인트 추가 #9"
```

---

## Task 11: 전체 빌드·아키텍처 검증

**Files:** (변경 없음 — 검증만)

- [ ] **Step 1: 전체 빌드(컴파일 + 전 테스트 + ArchUnit)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — 신규 auth 테스트 포함 전 모듈 통과. `ArchitectureTest`의 `domain → global 단방향`·`global → domain 금지` 규칙 위반 없음(auth는 domain→domain·domain→global만 사용).

- [ ] **Step 2: auth 도메인 테스트만 재확인(요약 카운트)**

Run: `./gradlew test --tests 'com.elipair.church.domain.auth.*'`
Expected: PASS — `AuthServiceTest`(9) + `AuthApiTest`(13) = 22 tests.

- [ ] **Step 3: 변경 파일 점검(미커밋 없음)**

Run: `git status`
Expected: working tree clean (Task 1~10에서 전부 커밋됨).

---

## Self-Review (작성자 점검 결과)

**1. 스펙 커버리지**
- signup(phone·name·password·email·약관2종·USER·uuid·201) → Task 3·4 ✓
- login(phone+password·동일401·tokens·member·requiresAgreement) → Task 5·6 ✓
- refresh(Redis 확인·Access만 재발급·권한 재조회) → Task 7·8 ✓
- logout(인증·access 블랙리스트·현재기기 refresh revoke) → Task 9·10 ✓
- 리뷰 반영(PhoneNumbers public·findByPhone EntityGraph·parse try/catch·SignupResponse position 제외) → Task 1·5·7·2 ✓

**2. 플레이스홀더 스캔** — TBD/“적절히 처리” 없음. 모든 step에 실제 코드·명령·기대값 포함. ✓

**3. 타입·시그니처 일관성**
- `AuthService` 메서드: `signup`/`login`/`refresh`/`logout` + private `issueTokens`/`principalOf`/`positionOf`/`parseToken`/`blacklistAccess`/`revokeRefreshIfOwned` — 정의·호출 일치.
- `JwtTokenProvider.TYPE_REFRESH`·`CLAIM_TYPE`·`CLAIM_PERMISSIONS`·`issueAccess(MemberPrincipal,String,List)`·`issueRefresh(String)`·`parse(String)` — 실제 시그니처와 일치.
- `RefreshTokenStore.save/isValid/revoke`·`TokenBlacklist.blacklist`·`MemberPrincipal(Long,String,String,int)`·`MemberAuthorities.permissions/maxPriority` — 일치.
- DTO: `MemberSummary`(position 有, login 전용)·`SignupResponse`(position 無) — Task 2 정의와 사용처 일치.
