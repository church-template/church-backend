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
        return new String[] {
            tokens.path("accessToken").asText(), tokens.path("refreshToken").asText()
        };
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

        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/auth/signup")
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

    @Test
    void withdraw_without_token_is_401() throws Exception {
        mockMvc.perform(delete("/api/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"any\"}"))
                .andExpect(status().isUnauthorized());
    }
}
