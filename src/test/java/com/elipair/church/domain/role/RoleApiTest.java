package com.elipair.church.domain.role;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleApiTest {

    private static final Set<String> SEED_ROLES = Set.of("SUPER_ADMIN", "ADMIN", "MEMBER", "USER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private RoleRepository roleRepository;

    /** 시드 역할은 보존하고 테스트가 만든 역할만 제거. */
    @AfterEach
    void cleanup() {
        roleRepository.deleteAll(roleRepository.findAll().stream()
                .filter(r -> !SEED_ROLES.contains(r.getName()))
                .toList());
    }

    /** maxPriority·permissions를 가진 요청자 토큰. */
    private String token(int maxPriority, String... permissions) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(1L, "uuid-admin", "관리자", maxPriority), null, List.of(permissions));
    }

    private String roleManager() {
        return token(1000, "ROLE_MANAGE");
    }

    @Test
    void lists_seed_roles_priority_desc_with_permissions() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header("Authorization", roleManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("SUPER_ADMIN")) // priority 1000 최상위
                .andExpect(jsonPath("$[0].permissions.length()").value(12))
                .andExpect(jsonPath("$.page").doesNotExist());
    }

    @Test
    void anonymous_is_401() throws Exception {
        mockMvc.perform(get("/api/admin/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void without_role_manage_is_403() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header("Authorization", token(100, "SERMON_WRITE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}
