package com.elipair.church.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import com.elipair.church.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
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
    @Autowired private JwtTokenProvider provider;

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
        Member m = persistMember("01012345678", "password123", "USER");
        m.resetAgreement("terms"); // 약관 개정 시뮬레이션 → termsAgreed=false
        memberRepository.saveAndFlush(m);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresAgreement").value(true));
    }

    private String loginAndReadToken(String phone, String rawPassword, String field) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"password\":\"" + rawPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("tokens").path(field).asText();
    }

    @Test
    void refresh_reissues_access_and_reflects_new_permission() throws Exception {
        Member m = persistMember("01012345678", "password123", "USER"); // USER = 권한 없음
        String refresh = loginAndReadToken("010-1234-5678", "password123", "refreshToken");

        // login 이후 MEMBER 역할 부여 → GALLERY_VIEW 생김
        Member managed = memberRepository.findByIdAndDeletedAtIsNull(m.getId()).orElseThrow();
        managed.grantRole(role("MEMBER"));
        memberRepository.saveAndFlush(managed);

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

    @Test
    void logout_blacklists_access_and_revokes_refresh() throws Exception {
        persistMember("01012345678", "password123", "USER");
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String access = objectMapper.readTree(body).path("tokens").path("accessToken").asText();
        String refresh = objectMapper.readTree(body).path("tokens").path("refreshToken").asText();

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
}
