package com.elipair.church.domain.role;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PermissionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    private String roleManager() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(1L, "uuid-admin", "관리자", 1000), null, List.of("ROLE_MANAGE"));
    }

    private String otherPermission() {
        return "Bearer "
                + provider.issueAccess(new MemberPrincipal(2L, "uuid-user", "사용자", 100), null, List.of("SERMON_WRITE"));
    }

    @Test
    void lists_fifteen_permissions_sorted_by_name() throws Exception {
        mockMvc.perform(get("/api/admin/permissions").header("Authorization", roleManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(15)) // V13 CHALLENGE_* 2건 + V14 INQUIRY_MANAGE 포함
                .andExpect(jsonPath("$[0].name").value("BULLETIN_WRITE")); // name ASC 첫 항목
    }

    @Test
    void anonymous_is_401() throws Exception {
        mockMvc.perform(get("/api/admin/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void without_role_manage_is_403() throws Exception {
        mockMvc.perform(get("/api/admin/permissions").header("Authorization", otherPermission()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}
