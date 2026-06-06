package com.elipair.church.domain.event;

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
class EventApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private EventRepository eventRepository;

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
        eventRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String token(Long memberId, String permission) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-" + memberId, "관리자", 1000), null, List.of(permission));
    }

    private String adminToken() {
        return token(authorId, "EVENT_WRITE");
    }

    private long createEvent(String body) throws Exception {
        String json = mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private String body(String title, String start, String end, boolean allDay) {
        String endJson = end == null ? "null" : "\"" + end + "\"";
        return """
                {"title":"%s","description":"본문 ![](media:42)","location":"본당","startAt":"%s","endAt":%s,"allDay":%s,"tagIds":[]}
                """.formatted(title, start, endJson, allDay);
    }

    @Test
    void create_as_event_write_returns_201_without_author_or_viewcount() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("부활절 연합예배", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("부활절 연합예배"))
                .andExpect(jsonPath("$.location").value("본당"))
                .andExpect(jsonPath("$.allDay").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.author").doesNotExist())
                .andExpect(jsonPath("$.viewCount").doesNotExist());
    }

    @Test
    void create_anonymous_is_401() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void create_without_permission_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", token(authorId, "MEDIA_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_blank_title_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_end_not_after_start_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("뒤집힘", "2026-06-10T11:00:00", "2026-06-10T10:00:00", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void public_list_by_year_month_returns_overlapping_and_omits_description() throws Exception {
        createEvent(body("6월행사", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        createEvent(body("수련회", "2026-06-28T00:00:00", "2026-07-02T00:00:00", false));
        createEvent(body("경계행사", "2026-06-30T22:00:00", "2026-07-01T00:00:00", false));

        // 6월: 세 건 모두 겹친다. 카드에 description 없음.
        mockMvc.perform(get("/api/events").param("year", "2026").param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].description").doesNotExist());
    }

    @Test
    void public_list_july_excludes_boundary_event_keeps_multiday() throws Exception {
        createEvent(body("6월행사", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        createEvent(body("수련회", "2026-06-28T00:00:00", "2026-07-02T00:00:00", false));
        createEvent(body("경계행사", "2026-06-30T22:00:00", "2026-07-01T00:00:00", false));

        // 7월: 수련회만(end_at 배타라 경계행사 제외, off-by-one 차단). start_at ASC.
        mockMvc.perform(get("/api/events").param("year", "2026").param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("수련회"));
    }

    @Test
    void public_list_orders_by_start_at_ascending() throws Exception {
        createEvent(body("나중", "2026-06-20T10:00:00", "2026-06-20T11:00:00", false));
        createEvent(body("먼저", "2026-06-05T10:00:00", "2026-06-05T11:00:00", false));

        mockMvc.perform(get("/api/events").param("year", "2026").param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("먼저"))
                .andExpect(jsonPath("$.content[1].title").value("나중"));
    }

    @Test
    void public_list_by_start_end_date_range() throws Exception {
        createEvent(body("범위안", "2026-06-15T10:00:00", "2026-06-15T11:00:00", false));
        createEvent(body("범위밖", "2026-07-15T10:00:00", "2026-07-15T11:00:00", false));

        mockMvc.perform(get("/api/events").param("startDate", "2026-06-01").param("endDate", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("범위안"));
    }

    @Test
    void public_list_partial_pair_is_400() throws Exception {
        mockMvc.perform(get("/api/events").param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void public_list_month_out_of_range_is_400() throws Exception {
        mockMvc.perform(get("/api/events").param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void public_list_end_before_start_is_400() throws Exception {
        mockMvc.perform(get("/api/events").param("startDate", "2026-06-30").param("endDate", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void detail_returns_description_and_no_viewcount() throws Exception {
        long id = createEvent(body("상세행사", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));

        mockMvc.perform(get("/api/events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("본문 ![](media:42)"))
                .andExpect(jsonPath("$.viewCount").doesNotExist())
                .andExpect(jsonPath("$.author").doesNotExist());
    }

    @Test
    void detail_unknown_is_404() throws Exception {
        mockMvc.perform(get("/api/events/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void put_full_update_changes_fields_and_bumps_version() throws Exception {
        long id = createEvent(body("원본", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        String update = """
                {"title":"수정행사","description":"수정","location":"교육관","startAt":"2026-06-11T09:00:00","endAt":"2026-06-11T10:00:00","allDay":true,"tagIds":[],"version":0}
                """;

        mockMvc.perform(put("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정행사"))
                .andExpect(jsonPath("$.allDay").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void put_with_stale_version_is_409() throws Exception {
        long id = createEvent(body("원본", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        String v0 = """
                {"title":"A","description":"c","location":"본당","startAt":"2026-06-10T10:00:00","endAt":"2026-06-10T11:00:00","allDay":false,"tagIds":[],"version":0}
                """;
        mockMvc.perform(put("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void scalar_patch_bumps_version_and_allows_immediate_next_edit() throws Exception {
        long id = createEvent(body("원본", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        // 스칼라 필드(title) 변경 PATCH: version 0 → 응답 version 1(flush 반영).
        mockMvc.perform(patch("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"1차수정","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("1차수정"))
                .andExpect(jsonPath("$.version").value(1));
        // 응답 version(1)으로 즉시 2차 수정 → 200.
        mockMvc.perform(patch("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"2차수정","version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void tag_only_patch_keeps_version_unchanged() throws Exception {
        long id = createEvent(body("원본", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        // tag-only PATCH(스칼라 불변): events 행 미변경이라 version 유지(설계 §5 Finding 2). tagIds=[]로 동일.
        mockMvc.perform(patch("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[],"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
        // version 0이 여전히 유효 → 동일 version으로 다시 통과.
        mockMvc.perform(patch("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[],"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void patch_end_before_start_is_400() throws Exception {
        long id = createEvent(body("원본", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        // 기존 start 6/10 10:00보다 이전으로 end만 변경 → 서비스 교차검증 400.
        mockMvc.perform(patch("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endAt":"2026-06-10T09:00:00","version":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void delete_soft_deletes_then_detail_404() throws Exception {
        long id = createEvent(body("삭제대상", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));

        mockMvc.perform(delete("/api/admin/events/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/events/" + id)).andExpect(status().isNotFound());
    }
}
