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
