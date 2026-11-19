package com.elipair.church.domain.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.event.EventRepository;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.notice.NoticeRepository;
import com.elipair.church.domain.sermon.SermonRepository;
import com.elipair.church.domain.visibility.VisibilityLevel;
import com.elipair.church.domain.visibility.VisibilityService;
import com.elipair.church.domain.visibility.dto.VisibilityUpdateRequest;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 홈 통합 조회의 공개 범위(#95, 설계 5.4·6.6-1). /api/main은 교회별로 캐시되므로 보는 사람과 무관하게
 * "방문자가 볼 수 있는 것"만 담아야 한다 — 교인이 먼저 열어도 교인 전용 설교가 방문자에게 새지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MainVisibilityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private VisibilityService visibility;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private SermonRepository sermonRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcClient jdbc;

    private Long memberId;

    @BeforeEach
    void seed() {
        cacheManager.getCache("main").clear();
        memberId = memberRepository
                .saveAndFlush(Member.create("01000000097", "관리목사", "{enc}", null, null, true, true))
                .getId();
        jdbc.sql("insert into notices(title, content, created_at, church_id) values ('홈공지', 'x', now(), 1)")
                .update();
        jdbc.sql("insert into events(title, start_at, created_at, church_id)"
                        + " values ('홈일정', now() + interval '1 day', now(), 1)")
                .update();
    }

    @AfterEach
    void cleanup() {
        set(VisibilityLevel.PUBLIC, VisibilityLevel.PUBLIC, VisibilityLevel.MEMBERS); // 기존 교회 기본값
        cacheManager.getCache("main").clear();
        sermonRepository.deleteAll();
        noticeRepository.deleteAll();
        eventRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private void set(VisibilityLevel notices, VisibilityLevel events, VisibilityLevel sermons) {
        visibility.update(
                new VisibilityUpdateRequest(notices, events, VisibilityLevel.PUBLIC, sermons, VisibilityLevel.MEMBERS));
    }

    private long insertSermon(String title, boolean membersOnly) {
        return jdbc.sql(
                        "insert into sermons(title, preacher, preached_at, content, created_at, church_id, members_only)"
                                + " values (?, '김목사', current_date, 'x', now(), 1, ?) returning id")
                .param(title)
                .param(membersOnly)
                .query(Long.class)
                .single();
    }

    private String token(String... permissions) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-" + memberId, "관리자", 1000), null, List.of(permissions));
    }

    private String home(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private String home() throws Exception {
        return home(get("/api/main"));
    }

    private static List<String> titles(String json, String section) {
        return JsonPath.read(json, "$." + section + "[*].title");
    }

    /** allEntries 무효화는 Redis SCAN 기반이라 전파 지연이 있다(MainCacheTest와 같은 이유) — 짧게 폴링한다. */
    private String homeUntil(Predicate<String> done) throws Exception {
        String json = home();
        for (int i = 0; i < 40 && !done.test(json); i++) {
            Thread.sleep(25);
            json = home();
        }
        return json;
    }

    @Test
    void members_only_sermon_never_appears_on_home() throws Exception {
        set(VisibilityLevel.PUBLIC, VisibilityLevel.PUBLIC, VisibilityLevel.PUBLIC);
        insertSermon("공개설교", false);
        insertSermon("교인설교", true);

        assertThat(titles(home(), "sermons")).contains("공개설교").doesNotContain("교인설교");
    }

    @Test
    void sermon_cards_still_appear_when_sermon_menu_is_members() throws Exception {
        // 은샘의 지금 홈: 설교 메뉴가 교인만이어도 카드는 공개, 글 단위 교인만은 그래도 빠진다
        insertSermon("공개설교", false);
        insertSermon("교인설교", true);

        assertThat(titles(home(), "sermons")).contains("공개설교").doesNotContain("교인설교");
    }

    @Test
    void notices_are_empty_when_notice_menu_is_members_and_present_when_public() throws Exception {
        assertThat(titles(home(), "notices")).containsExactly("홈공지"); // 대조: PUBLIC

        set(VisibilityLevel.MEMBERS, VisibilityLevel.PUBLIC, VisibilityLevel.MEMBERS);
        String json = homeUntil(j -> titles(j, "notices").isEmpty());
        assertThat(titles(json, "notices")).isEmpty();
        assertThat(titles(json, "upcomingEvents")).containsExactly("홈일정"); // 다른 메뉴는 그대로
    }

    @Test
    void events_are_empty_when_event_menu_is_members_and_present_when_public() throws Exception {
        assertThat(titles(home(), "upcomingEvents")).containsExactly("홈일정"); // 대조: PUBLIC

        set(VisibilityLevel.PUBLIC, VisibilityLevel.MEMBERS, VisibilityLevel.MEMBERS);
        String json = homeUntil(j -> titles(j, "upcomingEvents").isEmpty());
        assertThat(titles(json, "upcomingEvents")).isEmpty();
        assertThat(titles(json, "notices")).containsExactly("홈공지");
    }

    @Test
    void home_requested_by_member_first_does_not_leak_to_anonymous() throws Exception {
        set(VisibilityLevel.PUBLIC, VisibilityLevel.PUBLIC, VisibilityLevel.PUBLIC);
        insertSermon("공개설교", false);
        insertSermon("교인설교", true);

        String member = home(get("/api/main").header("Authorization", token("SERMON_VIEW", "SERMON_WRITE")));
        String anonymous = home();

        assertThat(titles(member, "sermons")).contains("공개설교").doesNotContain("교인설교");
        assertThat(titles(anonymous, "sermons")).contains("공개설교").doesNotContain("교인설교");
    }

    @Test
    void visibility_update_evicts_home_cache() throws Exception {
        assertThat(titles(home(), "notices")).containsExactly("홈공지"); // 캐시 적재

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/visibility")
                        .header("Authorization", token("VISIBILITY_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notices":"MEMBERS","events":"PUBLIC","bulletins":"PUBLIC",
                                 "sermons":"MEMBERS","gallery":"MEMBERS"}
                                """))
                .andExpect(status().isOk());

        assertThat(titles(homeUntil(j -> titles(j, "notices").isEmpty()), "notices"))
                .isEmpty();
    }

    @Test
    void toggling_sermon_members_only_evicts_home_cache() throws Exception {
        long id = insertSermon("곧교인설교", false);
        assertThat(titles(home(), "sermons")).contains("곧교인설교"); // 캐시 적재

        mvc.perform(patch("/api/admin/sermons/" + id)
                        .header("Authorization", token("SERMON_WRITE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membersOnly\":true,\"version\":0}"))
                .andExpect(status().isOk());

        assertThat(titles(homeUntil(j -> !titles(j, "sermons").contains("곧교인설교")), "sermons"))
                .doesNotContain("곧교인설교");
    }
}
