package com.elipair.church.domain.sermon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class SermonApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private SermonRepository sermonRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long authorId;

    @BeforeEach
    void seedAuthor() {
        Member author =
                memberRepository.saveAndFlush(Member.create("01000000000", "관리목사", "{enc}", null, null, true, true));
        authorId = author.getId();
    }

    @AfterEach
    void cleanup() {
        sermonRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String token(Long memberId, String permission) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-" + memberId, "관리자", 1000), null, List.of(permission));
    }

    private String adminToken() {
        return token(authorId, "SERMON_WRITE");
    }

    private static final String CREATE_BODY = """
            {"title":"산상수훈 강해 1","preacher":"김목사","series":"산상수훈","scripture":"마 5:1-12",
             "content":"본문 ![](media:42)","videoUrl":"https://youtu.be/abc","audioUrl":null,
             "preachedAt":"2026-06-01","tagIds":[]}
            """;

    private long createSermon() throws Exception {
        String json = mockMvc.perform(post("/api/admin/sermons")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void create_as_sermon_write_returns_201_with_author_and_zero_views() throws Exception {
        mockMvc.perform(post("/api/admin/sermons")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("산상수훈 강해 1"))
                .andExpect(jsonPath("$.content").value("본문 ![](media:42)"))
                .andExpect(jsonPath("$.viewCount").value(0))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.author").value("관리목사"));
    }

    @Test
    void create_anonymous_is_401() throws Exception {
        mockMvc.perform(post("/api/admin/sermons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void create_without_permission_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/sermons")
                        .header("Authorization", token(authorId, "MEDIA_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_blank_title_is_400() throws Exception {
        String bad = CREATE_BODY.replace("산상수훈 강해 1", "");
        mockMvc.perform(post("/api/admin/sermons")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void public_list_paginates_and_omits_content() throws Exception {
        createSermon();
        createSermon();

        mockMvc.perform(get("/api/sermons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").exists())
                .andExpect(jsonPath("$.content[0].author").value("관리목사"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
    }

    @Test
    void public_list_filters_by_preacher() throws Exception {
        createSermon(); // 김목사
        mockMvc.perform(get("/api/sermons").param("preacher", "김목사"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(get("/api/sermons").param("preacher", "없는목사"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void public_detail_increments_view_count() throws Exception {
        long id = createSermon();

        mockMvc.perform(get("/api/sermons/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1));
        mockMvc.perform(get("/api/sermons/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(2));
    }

    @Test
    void detail_unknown_is_404() throws Exception {
        mockMvc.perform(get("/api/sermons/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void put_full_update_changes_fields_and_bumps_version() throws Exception {
        long id = createSermon();
        String body = """
                {"title":"수정된 제목","preacher":"이목사","series":null,"scripture":null,
                 "content":"수정 본문","videoUrl":null,"audioUrl":null,"preachedAt":"2026-07-01",
                 "tagIds":[],"version":0}
                """;

        mockMvc.perform(put("/api/admin/sermons/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.preacher").value("이목사"))
                .andExpect(jsonPath("$.series").doesNotExist())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void put_with_stale_version_is_409() throws Exception {
        long id = createSermon();
        String v0 = """
                {"title":"A","preacher":"김목사","series":null,"scripture":null,"content":"c",
                 "videoUrl":null,"audioUrl":null,"preachedAt":"2026-07-01","tagIds":[],"version":0}
                """;
        mockMvc.perform(put("/api/admin/sermons/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/sermons/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void patch_partial_updates_only_given_field() throws Exception {
        long id = createSermon();
        String body = """
                {"title":"부분수정","version":0}
                """;

        mockMvc.perform(patch("/api/admin/sermons/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("부분수정"))
                .andExpect(jsonPath("$.preacher").value("김목사"));
    }

    @Test
    void delete_soft_deletes_then_detail_404() throws Exception {
        long id = createSermon();

        mockMvc.perform(delete("/api/admin/sermons/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/sermons/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void author_is_masked_when_member_withdrawn() throws Exception {
        long id = createSermon();
        Member author = memberRepository.findById(authorId).orElseThrow();
        author.softDelete();
        memberRepository.saveAndFlush(author);

        mockMvc.perform(get("/api/sermons/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("(탈퇴한 사용자)"));
    }
}
