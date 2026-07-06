package com.elipair.church.domain.challenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
class BibleChallengeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private BibleChallengeRepository challengeRepository;

    @Autowired
    private ChallengeParticipationRepository participationRepository;

    @Autowired
    private ChallengeReadingLogRepository logRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;

    @BeforeEach
    void seed() {
        Member member =
                memberRepository.saveAndFlush(Member.create("01000000000", "김통독", "{enc}", null, null, true, true));
        memberId = member.getId();
    }

    @AfterEach
    void cleanup() {
        logRepository.deleteAll();
        participationRepository.deleteAll();
        challengeRepository.deleteAll(challengeRepository.findAll());
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String adminToken() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-admin", "관리자", 900),
                        null,
                        List.of("CHALLENGE_MANAGE", "CHALLENGE_PARTICIPATE"));
    }

    private String memberToken() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-member", "교인", 100),
                        null,
                        List.of("CHALLENGE_PARTICIPATE"));
    }

    private String userToken() {
        return "Bearer "
                + provider.issueAccess(new MemberPrincipal(memberId, "uuid-user", "미승인", 0), null, List.of());
    }

    /** 신약 60일 챌린지(오늘 10일차) 생성 → id. dailyGoal = ceil(260/60) = 5. */
    private long createNtChallenge() throws Exception {
        String json = mockMvc.perform(post("/api/admin/bible-challenges")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"학생부 신약 60일","description":"방학 통독","startBook":40,"endBook":66,
                                 "startDate":"%s","targetDays":60}
                                """.formatted(LocalDate.now().minusDays(9))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // ---- 인가 3단계(설계 §4) ----

    @Test
    void list_anonymous_is_401() throws Exception {
        mockMvc.perform(get("/api/bible-challenges"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void list_user_without_participate_is_403() throws Exception {
        mockMvc.perform(get("/api/bible-challenges").header("Authorization", userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void admin_create_without_manage_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/bible-challenges")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","startBook":1,"endBook":66,"startDate":"2026-01-01","targetDays":100}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ---- 관리자 CRUD ----

    @Test
    void create_returns_201_with_derived_fields() throws Exception {
        mockMvc.perform(post("/api/admin/bible-challenges")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"신약 60일","startBook":40,"endBook":66,"startDate":"2026-07-01","targetDays":60}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalChapters").value(260))
                .andExpect(jsonPath("$.dailyGoal").value(5))
                .andExpect(jsonPath("$.endDate").value("2026-08-29"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void create_inverted_range_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/bible-challenges")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"역순","startBook":66,"endBook":40,"startDate":"2026-07-01","targetDays":60}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void patch_bumps_version_then_stale_is_409() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(patch("/api/admin/bible-challenges/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"수정","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(patch("/api/admin/bible-challenges/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"또수정","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void patch_structure_with_participant_is_400() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/admin/bible-challenges/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetDays":90,"version":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void delete_soft_deletes_then_detail_404() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(delete("/api/admin/bible-challenges/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/bible-challenges/" + id).header("Authorization", memberToken()))
                .andExpect(status().isNotFound());
    }

    // ---- 회원: 목록/상세/참여 ----

    @Test
    void list_returns_page_envelope_with_status() throws Exception {
        createNtChallenge();
        mockMvc.perform(get("/api/bible-challenges").header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("ONGOING"))
                .andExpect(jsonPath("$.content[0].description").doesNotExist());
    }

    @Test
    void detail_shows_joined_flag() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(get("/api/bible-challenges/" + id).header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(false));
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/bible-challenges/" + id).header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(true));
    }

    @Test
    void join_twice_is_409() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    // ---- 회원: 읽음 기록 흐름 ----

    @Test
    void read_default_then_progress_reflects_daily_goal() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaptersRead").value(5))
                .andExpect(jsonPath("$.todayDone").value(true))
                .andExpect(jsonPath("$.currentPosition.book").value("마태복음"))
                .andExpect(jsonPath("$.currentPosition.chapter").value(5))
                .andExpect(jsonPath("$.streakDays").value(1));
    }

    @Test
    void read_backfill_yesterday_heals_streak() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());

        // 오늘 기록 → 어제 소급 → 스트릭 2 (설계 §3 백필 치유)
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":5}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":5,\"date\":\"%s\"}".formatted(LocalDate.now().minusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streakDays").value(2))
                .andExpect(jsonPath("$.chaptersRead").value(10));
    }

    @Test
    void read_future_date_is_400() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":5,\"date\":\"%s\"}".formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void read_without_join_is_404() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void cancel_today_rolls_back() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":8}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/bible-challenges/" + id + "/read").header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaptersRead").value(0))
                .andExpect(jsonPath("$.todayChapters").value(0));
    }

    @Test
    void cancel_without_log_is_404() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/bible-challenges/" + id + "/read").header("Authorization", memberToken()))
                .andExpect(status().isNotFound());
    }

    // ---- 회원: 로그/마이페이지 ----

    @Test
    void my_logs_returns_dated_entries() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":5}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bible-challenges/" + id + "/my-logs").header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].readDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$[0].chapters").value(5));
    }

    @Test
    void my_participations_lists_history_with_completion() throws Exception {
        long id = createNtChallenge();
        mockMvc.perform(post("/api/bible-challenges/" + id + "/join").header("Authorization", memberToken()))
                .andExpect(status().isCreated());
        // 260장 전부 → 1회독 완료
        mockMvc.perform(post("/api/bible-challenges/" + id + "/read")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapters\":260}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundsCompleted").value(1));

        mockMvc.perform(get("/api/bible-challenges/my-participations").header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].completed").value(true))
                .andExpect(jsonPath("$.content[0].roundsCompleted").value(1))
                .andExpect(jsonPath("$.content[0].challenge.title").value("학생부 신약 60일"));
    }
}
