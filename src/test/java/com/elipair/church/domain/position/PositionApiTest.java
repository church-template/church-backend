package com.elipair.church.domain.position;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class PositionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private PositionRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    private String admin() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(1L, "uuid-admin", "관리자", 1000), null, List.of("POSITION_MANAGE"));
    }

    private String otherPermission() {
        return "Bearer "
                + provider.issueAccess(new MemberPrincipal(2L, "uuid-user", "사용자", 100), null, List.of("SERMON_WRITE"));
    }

    private String body(String name, Integer sortOrder) {
        return sortOrder == null
                ? "{\"name\":\"" + name + "\"}"
                : "{\"name\":\"" + name + "\",\"sortOrder\":" + sortOrder + "}";
    }

    /** 관리자로 직분 생성 후 생성된 id 반환. */
    private long createPosition(String name, int sortOrder) throws Exception {
        String json = mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, sortOrder)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void public_list_is_open_and_sorted_as_plain_array() throws Exception {
        createPosition("목사", 20);
        createPosition("장로", 10);

        mockMvc.perform(get("/api/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sortOrder").value(10))
                .andExpect(jsonPath("$[1].sortOrder").value(20))
                .andExpect(jsonPath("$.page").doesNotExist());
    }

    @Test
    void create_without_sort_order_appends_with_gap() throws Exception {
        mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("목사", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("목사"))
                .andExpect(jsonPath("$.sortOrder").value(10));

        mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("장로", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(20));
    }

    @Test
    void create_anonymous_is_401() throws Exception {
        mockMvc.perform(post("/api/admin/positions")
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("목사", 10)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void create_without_permission_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", otherPermission())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("목사", 10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_blank_name_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ", 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_duplicate_name_is_409() throws Exception {
        createPosition("목사", 10);

        mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("목사", 20)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void patch_updates_sort_order_only() throws Exception {
        long id = createPosition("목사", 10);

        mockMvc.perform(patch("/api/admin/positions/" + id)
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("목사"))
                .andExpect(jsonPath("$.sortOrder").value(99));
    }

    @Test
    void patch_updates_name() throws Exception {
        long id = createPosition("목사", 10);

        mockMvc.perform(patch("/api/admin/positions/" + id)
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"부목사\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("부목사"))
                .andExpect(jsonPath("$.sortOrder").value(10));

        // 새 이름이 DB에 실제 반영됐는지 목록으로 확인
        mockMvc.perform(get("/api/positions")).andExpect(jsonPath("$[0].name").value("부목사"));
    }

    @Test
    void patch_unknown_id_is_404() throws Exception {
        mockMvc.perform(patch("/api/admin/positions/999999")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("목사", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void delete_returns_204_and_removes() throws Exception {
        long id = createPosition("목사", 10);

        mockMvc.perform(delete("/api/admin/positions/" + id).header("Authorization", admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/positions")).andExpect(jsonPath("$.length()").value(0));
    }
}
