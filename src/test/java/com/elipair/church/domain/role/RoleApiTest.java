package com.elipair.church.domain.role;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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

    private long createRole(String name, int priority) throws Exception {
        String json = mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"priority\":" + priority + ",\"description\":\"d\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void create_returns_201_non_system_empty_permissions() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"EDITOR\",\"priority\":500,\"description\":\"편집자\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("EDITOR"))
                .andExpect(jsonPath("$.isSystem").value(false))
                .andExpect(jsonPath("$.permissions.length()").value(0));
    }

    @Test
    void create_priority_above_requester_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", token(900, "ROLE_MANAGE"))
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TOOHIGH\",\"priority\":901,\"description\":\"d\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_duplicate_name_is_409() throws Exception {
        createRole("EDITOR", 500);

        mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"EDITOR\",\"priority\":600,\"description\":\"d\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void create_blank_name_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"priority\":500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void patch_updates_priority_within_level() throws Exception {
        long id = createRole("EDITOR", 500);

        mockMvc.perform(patch("/api/admin/roles/" + id)
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":700}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("EDITOR"))
                .andExpect(jsonPath("$.priority").value(700));
    }

    @Test
    void patch_system_role_is_403() throws Exception {
        long systemId = roleRepository.findAll().stream()
                .filter(r -> r.getName().equals("ADMIN"))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(patch("/api/admin/roles/" + systemId)
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"변경시도\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void patch_unknown_id_is_404() throws Exception {
        mockMvc.perform(patch("/api/admin/roles/999999")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
