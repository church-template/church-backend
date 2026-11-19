package com.elipair.church.domain.sermon;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.visibility.VisibilityLevel;
import com.elipair.church.domain.visibility.VisibilityService;
import com.elipair.church.domain.visibility.dto.VisibilityUpdateRequest;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 설교 한 편의 "교인만"(#95, 설계 5.1·6.6). 설교 메뉴를 PUBLIC으로 열고 시작한다 —
 * 메뉴가 MEMBERS면 글 표시는 효과가 없으므로(메뉴 판단이 먼저 막는다) 글 단위 규칙은 PUBLIC에서만 보인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SermonVisibilityApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private VisibilityService visibility;

    @Autowired
    private SermonRepository sermonRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcClient jdbc;

    private Long memberId;
    private long plainA;
    private long plainB;
    private long membersOnly;

    @BeforeEach
    void seed() {
        setSermonMenu(VisibilityLevel.PUBLIC);
        memberId = memberRepository
                .saveAndFlush(Member.create("01000000095", "관리목사", "{enc}", null, null, true, true))
                .getId();
        plainA = insertSermon("공개설교A", false);
        plainB = insertSermon("공개설교B", false);
        membersOnly = insertSermon("교인설교", true);
    }

    @AfterEach
    void cleanup() {
        setSermonMenu(VisibilityLevel.MEMBERS); // 기존 교회 기본값으로 되돌린다
        sermonRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private void setSermonMenu(VisibilityLevel sermons) {
        visibility.update(new VisibilityUpdateRequest(
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                sermons,
                VisibilityLevel.MEMBERS));
    }

    private long insertSermon(String title, boolean flag) {
        return jdbc.sql(
                        "insert into sermons(title, preacher, preached_at, content, created_at, church_id, members_only)"
                                + " values (?, '김목사', current_date, '본문', now(), 1, ?) returning id")
                .param(title)
                .param(flag)
                .query(Long.class)
                .single();
    }

    private String token(String... permissions) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-" + memberId, "관리자", 1000), null, List.of(permissions));
    }

    private String viewer() {
        return token("SERMON_VIEW");
    }

    private String writer() {
        return token("SERMON_WRITE", "SERMON_VIEW");
    }

    private String read(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void anonymous_list_omits_members_only_sermons_and_total_counts_only_public() throws Exception {
        mvc.perform(get("/api/sermons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.title == '교인설교')]").isEmpty())
                .andExpect(jsonPath("$.content[?(@.title == '공개설교A')]").isNotEmpty());
    }

    @Test
    void logged_in_without_sermon_view_list_omits_members_only_sermons() throws Exception {
        // 가입만 한 사람(조회 권한 없음)도 방문자와 같다 — 로그인 여부가 아니라 권한으로 가른다(설계 5.1)
        mvc.perform(get("/api/sermons").header("Authorization", token("MEDIA_MANAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.title == '교인설교')]").isEmpty());
    }

    @Test
    void member_list_includes_members_only_sermons_with_flag() throws Exception {
        mvc.perform(get("/api/sermons").header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[?(@.membersOnly == true)]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.membersOnly == true)].title").value("교인설교"));
    }

    @Test
    void anonymous_detail_of_members_only_is_401_and_view_count_unchanged() throws Exception {
        mvc.perform(get("/api/sermons/" + membersOnly))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        // 막힌 요청은 조회수를 올리지 않는다 — 교인의 첫 조회가 1이어야 한다(버퍼 포함 값)
        mvc.perform(get("/api/sermons/" + membersOnly).header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    void logged_in_without_sermon_view_detail_of_members_only_is_403() throws Exception {
        mvc.perform(get("/api/sermons/" + membersOnly).header("Authorization", token("MEDIA_MANAGE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        // 대조: 같은 토큰으로 표시 없는 설교는 열린다
        mvc.perform(get("/api/sermons/" + plainA).header("Authorization", token("MEDIA_MANAGE")))
                .andExpect(status().isOk());
    }

    @Test
    void member_detail_of_members_only_is_200() throws Exception {
        mvc.perform(get("/api/sermons/" + membersOnly).header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("교인설교"))
                .andExpect(jsonPath("$.membersOnly").value(true));
    }

    @Test
    void anonymous_detail_of_plain_sermon_is_200_and_view_count_increases() throws Exception {
        mvc.perform(get("/api/sermons/" + plainA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(false))
                .andExpect(jsonPath("$.viewCount").value(1));
        mvc.perform(get("/api/sermons/" + plainA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(2));
    }

    @Test
    void members_only_filter_is_ignored_without_sermon_write() throws Exception {
        // 방문자가 필터로 교인 전용 글 개수를 알아내지 못한다(설계 6.6-6)
        mvc.perform(get("/api/sermons").param("membersOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
        // 교인도 쓰기 권한이 없으면 필터가 무시돼 전부 본다
        mvc.perform(get("/api/sermons").param("membersOnly", "true").header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void members_only_filter_works_for_sermon_write() throws Exception {
        mvc.perform(get("/api/sermons").param("membersOnly", "true").header("Authorization", writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("교인설교"));
        mvc.perform(get("/api/sermons").param("membersOnly", "false").header("Authorization", writer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void flag_survives_menu_round_trip() throws Exception {
        setSermonMenu(VisibilityLevel.MEMBERS);
        setSermonMenu(VisibilityLevel.PUBLIC);
        mvc.perform(get("/api/sermons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/sermons/" + membersOnly).header("Authorization", viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true));
    }

    @Test
    void create_without_field_defaults_to_false_and_patch_null_keeps_value() throws Exception {
        String created = read(mvc.perform(post("/api/admin/sermons")
                        .header("Authorization", writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"새설교","preacher":"김목사","preachedAt":"2026-06-01"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.membersOnly").value(false)));
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mvc.perform(patch("/api/admin/sermons/" + id)
                        .header("Authorization", writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membersOnly\":true,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true));
        // PATCH에서 빠진 membersOnly는 미변경
        mvc.perform(patch("/api/admin/sermons/" + id)
                        .header("Authorization", writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목만\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true));
    }

    @Test
    void put_without_members_only_field_keeps_members_only_true() throws Exception {
        // 아직 membersOnly를 모르는 옛 수정 화면이 PUT을 보내도 교인 전용 설교가 공개로 풀리지 않는다
        mvc.perform(put("/api/admin/sermons/" + membersOnly)
                        .header("Authorization", writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"교인설교","preacher":"김목사","preachedAt":"2026-06-01","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true));
        mvc.perform(get("/api/sermons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.title == '교인설교')]").isEmpty());
    }

    @Test
    void put_with_false_clears_it() throws Exception {
        mvc.perform(put("/api/admin/sermons/" + membersOnly)
                        .header("Authorization", writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"교인설교","preacher":"김목사","preachedAt":"2026-06-01",\
                                "membersOnly":false,"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(false));
        mvc.perform(get("/api/sermons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3));
    }

    @Test
    void card_carries_detail_version_and_patch_with_it_toggles_members_only() throws Exception {
        mvc.perform(patch("/api/admin/sermons/" + plainA)
                        .header("Authorization", writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"공개설교A2\",\"version\":0}"))
                .andExpect(status().isOk());

        String list = read(mvc.perform(get("/api/sermons").header("Authorization", writer())));
        List<Integer> cardVersions = JsonPath.read(list, "$.content[?(@.id == " + plainA + ")].version");
        int cardVersion = cardVersions.getFirst();
        String detail = read(mvc.perform(get("/api/sermons/" + plainA).header("Authorization", writer())));
        int detailVersion = JsonPath.read(detail, "$.version");
        org.assertj.core.api.Assertions.assertThat(cardVersion).isEqualTo(1).isEqualTo(detailVersion);

        // 목록에서 바로 전환: 카드 version으로 PATCH가 통한다
        mvc.perform(patch("/api/admin/sermons/" + plainA)
                        .header("Authorization", writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membersOnly\":true,\"version\":" + cardVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersOnly").value(true));
        // 같은(이제 낡은) version으로 다시 보내면 409
        mvc.perform(patch("/api/admin/sermons/" + plainA)
                        .header("Authorization", writer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membersOnly\":false,\"version\":" + cardVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }
}
