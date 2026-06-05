package com.elipair.church.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import tools.jackson.databind.ObjectMapper;
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
