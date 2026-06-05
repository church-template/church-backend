package com.elipair.church.domain.tag;

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
class TagApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private TagRepository repository;

    @Autowired
    private ContentTagRepository contentTagRepository;

    @AfterEach
    void cleanup() {
        contentTagRepository.deleteAll();
        repository.deleteAll();
    }

    private String admin() {
        return "Bearer "
                + provider.issueAccess(new MemberPrincipal(1L, "uuid-admin", "관리자", 1000), null, List.of("TAG_MANAGE"));
    }

    private String otherPermission() {
        return "Bearer "
                + provider.issueAccess(new MemberPrincipal(2L, "uuid-user", "사용자", 100), null, List.of("SERMON_WRITE"));
    }

    private String body(String name) {
        return "{\"name\":\"" + name + "\"}";
    }

    private long createTag(String name) throws Exception {
        String json = mockMvc.perform(post("/api/admin/tags")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void public_list_is_open_and_sorted_as_plain_array() throws Exception {
        createTag("예배");
        createTag("봉사");

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("봉사"))
                .andExpect(jsonPath("$[1].name").value("예배"))
                .andExpect(jsonPath("$.page").doesNotExist());
    }

    @Test
    void create_anonymous_is_401() throws Exception {
        mockMvc.perform(post("/api/admin/tags")
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("예배")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void create_without_permission_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/tags")
                        .header("Authorization", otherPermission())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("예배")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_blank_name_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/tags")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_name_over_50_chars_is_400_not_409() throws Exception {
        mockMvc.perform(post("/api/admin/tags")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("a".repeat(51))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_duplicate_name_is_409() throws Exception {
        createTag("예배");

        mockMvc.perform(post("/api/admin/tags")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("예배")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void patch_renames() throws Exception {
        long id = createTag("예배");

        mockMvc.perform(patch("/api/admin/tags/" + id)
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("주일예배")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("주일예배"));

        mockMvc.perform(get("/api/tags")).andExpect(jsonPath("$[0].name").value("주일예배"));
    }

    @Test
    void patch_unknown_id_is_404() throws Exception {
        mockMvc.perform(patch("/api/admin/tags/999999")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("예배")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void delete_returns_204_and_removes() throws Exception {
        long id = createTag("예배");

        mockMvc.perform(delete("/api/admin/tags/" + id).header("Authorization", admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tags")).andExpect(jsonPath("$.length()").value(0));
    }
}
