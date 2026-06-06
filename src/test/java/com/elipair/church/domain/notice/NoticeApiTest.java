package com.elipair.church.domain.notice;

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
class NoticeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private NoticeRepository noticeRepository;

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
        noticeRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String token(Long memberId, String permission) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-" + memberId, "관리자", 1000), null, List.of(permission));
    }

    private String adminToken() {
        return token(authorId, "NOTICE_WRITE");
    }

    private static final String CREATE_BODY = """
            {"title":"2026 부활절 안내","content":"본문 ![](media:42)","isPinned":false,"tagIds":[]}
            """;

    private long createNotice(String body) throws Exception {
        String json = mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void create_as_notice_write_returns_201_with_author_and_zero_views() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("2026 부활절 안내"))
                .andExpect(jsonPath("$.content").value("본문 ![](media:42)"))
                .andExpect(jsonPath("$.isPinned").value(false))
                .andExpect(jsonPath("$.viewCount").value(0))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.author").value("관리목사"));
    }

    @Test
    void create_anonymous_is_401() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void create_without_permission_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", token(authorId, "MEDIA_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_blank_title_is_400() throws Exception {
        String bad = CREATE_BODY.replace("2026 부활절 안내", "");
        mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void public_list_paginates_and_omits_content() throws Exception {
        createNotice(CREATE_BODY);
        createNotice(CREATE_BODY);

        mockMvc.perform(get("/api/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").exists())
                .andExpect(jsonPath("$.content[0].author").value("관리목사"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
    }

    @Test
    void public_list_orders_pinned_first() throws Exception {
        // 고정 공지를 먼저(=더 오래된 created_at), 일반 공지를 나중(=더 최신)에 만든다.
        createNotice("""
                {"title":"고정공지","content":"c","isPinned":true,"tagIds":[]}
                """);
        createNotice("""
                {"title":"일반공지","content":"c","isPinned":false,"tagIds":[]}
                """);

        // 기본 정렬 is_pinned DESC, created_at DESC → 더 오래됐어도 고정이 먼저.
        mockMvc.perform(get("/api/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("고정공지"))
                .andExpect(jsonPath("$.content[0].isPinned").value(true))
                .andExpect(jsonPath("$.content[1].title").value("일반공지"));
    }

    @Test
    void public_list_filters_by_title_keyword() throws Exception {
        createNotice(CREATE_BODY); // title: "2026 부활절 안내"

        mockMvc.perform(get("/api/notices").param("q", "부활절"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mockMvc.perform(get("/api/notices").param("q", "없는키워드"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void public_detail_increments_view_count() throws Exception {
        long id = createNotice(CREATE_BODY);

        mockMvc.perform(get("/api/notices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1));
        mockMvc.perform(get("/api/notices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(2));
    }

    @Test
    void detail_unknown_is_404() throws Exception {
        mockMvc.perform(get("/api/notices/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void put_full_update_changes_fields_and_bumps_version() throws Exception {
        long id = createNotice(CREATE_BODY);
        String body = """
                {"title":"수정된 제목","content":"수정 본문","isPinned":true,"tagIds":[],"version":0}
                """;

        mockMvc.perform(put("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.isPinned").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void put_with_stale_version_is_409() throws Exception {
        long id = createNotice(CREATE_BODY);
        String v0 = """
                {"title":"A","content":"c","isPinned":false,"tagIds":[],"version":0}
                """;
        mockMvc.perform(put("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void patch_toggles_pin_and_keeps_other_fields() throws Exception {
        long id = createNotice(CREATE_BODY);
        String body = """
                {"isPinned":true,"version":0}
                """;

        mockMvc.perform(patch("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPinned").value(true))
                .andExpect(jsonPath("$.title").value("2026 부활절 안내"));
    }

    @Test
    void patch_response_version_allows_immediate_next_edit() throws Exception {
        long id = createNotice(CREATE_BODY);
        // 1차 PATCH(tagIds 미제공): version 0 → 응답 version은 1이어야 함(flush 반영, stale 409 회피 회귀 가드).
        mockMvc.perform(patch("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"1차수정","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // 응답 version(1)으로 즉시 2차 수정 → stale 409가 아니라 200.
        mockMvc.perform(patch("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"2차수정","version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("2차수정"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void delete_soft_deletes_then_detail_404() throws Exception {
        long id = createNotice(CREATE_BODY);

        mockMvc.perform(delete("/api/admin/notices/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notices/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void author_is_masked_when_member_withdrawn() throws Exception {
        long id = createNotice(CREATE_BODY);
        Member author = memberRepository.findById(authorId).orElseThrow();
        author.softDelete();
        memberRepository.saveAndFlush(author);

        mockMvc.perform(get("/api/notices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("(탈퇴한 사용자)"));
    }
}
