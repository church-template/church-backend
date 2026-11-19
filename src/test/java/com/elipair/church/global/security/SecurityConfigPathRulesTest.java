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

    // 갤러리·설교 경로의 401/403/200은 고정 매처가 아니라 교회 설정을 따른다 — MenuVisibilityApiTest(#95).

    @Test
    void challenge_path_without_challenge_participate_is_403() throws Exception {
        mockMvc.perform(get("/api/bible-challenges/ping").header("Authorization", bearer(List.of("SERMON_WRITE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void challenge_path_with_challenge_participate_is_200() throws Exception {
        mockMvc.perform(get("/api/bible-challenges/ping")
                        .header("Authorization", bearer(List.of("CHALLENGE_PARTICIPATE"))))
                .andExpect(status().isOk());
    }

    @Test
    void challenge_path_anonymous_is_401_invalid_token() throws Exception {
        mockMvc.perform(get("/api/bible-challenges/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void vehicle_path_without_vehicle_apply_is_403() throws Exception {
        mockMvc.perform(get("/api/vehicle-runs/ping").header("Authorization", bearer(List.of("SERMON_WRITE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void vehicle_path_with_vehicle_apply_is_200() throws Exception {
        mockMvc.perform(get("/api/vehicle-runs/ping").header("Authorization", bearer(List.of("VEHICLE_APPLY"))))
                .andExpect(status().isOk());
    }

    @Test
    void vehicle_path_anonymous_is_401_invalid_token() throws Exception {
        mockMvc.perform(get("/api/vehicle-runs/ping"))
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
