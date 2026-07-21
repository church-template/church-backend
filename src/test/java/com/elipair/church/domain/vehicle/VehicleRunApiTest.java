package com.elipair.church.domain.vehicle;

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
import java.time.Clock;
import java.time.LocalDateTime;
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
class VehicleRunApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private VehicleRunRepository runRepository;

    @Autowired
    private VehicleRequestRepository requestRepository;

    @Autowired
    private MemberRepository memberRepository;

    /** 서버의 "오늘"(APP_TIMEZONE) 기준 — JVM 기본 존과 다르면 마감 경계 테스트가 flake라 앱 Clock을 그대로 쓴다. */
    @Autowired
    private Clock clock;

    private Long memberId;

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    @BeforeEach
    void seed() {
        Member member =
                memberRepository.saveAndFlush(Member.create("01011112222", "김차량", "{enc}", null, null, true, true));
        memberId = member.getId();
    }

    @AfterEach
    void cleanup() {
        requestRepository.deleteAll();
        runRepository.deleteAll(runRepository.findAll());
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String adminToken() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-admin", "관리자", 900),
                        null,
                        List.of("VEHICLE_MANAGE", "VEHICLE_APPLY"));
    }

    private String memberToken() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-member", "교인", 100), null, List.of("VEHICLE_APPLY"));
    }

    private String userToken() {
        return "Bearer " + provider.issueAccess(new MemberPrincipal(memberId, "uuid-user", "미승인", 0), null, List.of());
    }

    /** 3일 뒤 오전 운행일 생성 → id. */
    private long createUpcomingRun() throws Exception {
        return createRun(now().plusDays(3), "토요일 오후, 학원 앞 경유");
    }

    private long createRun(LocalDateTime departsAt, String note) throws Exception {
        String json = mockMvc.perform(post("/api/admin/vehicle-runs")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"departsAt":"%s","note":"%s"}
                                """.formatted(departsAt, note)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // ---- 인가 ----

    @Test
    void admin_create_without_manage_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/vehicle-runs")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"departsAt":"2026-08-01T09:30:00"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ---- 관리자 CRUD ----

    @Test
    void create_returns_201_with_version_0() throws Exception {
        mockMvc.perform(post("/api/admin/vehicle-runs")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"departsAt":"2026-08-01T09:30:00","note":"주일 1부"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value("주일 1부"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void create_without_departs_at_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/vehicle-runs")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"날짜 없음"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void patch_bumps_version_then_stale_is_409() throws Exception {
        long id = createUpcomingRun();
        mockMvc.perform(patch("/api/admin/vehicle-runs/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"경유지 변경","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("경유지 변경"))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(patch("/api/admin/vehicle-runs/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"또 변경","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void delete_soft_deletes_then_patch_404() throws Exception {
        long id = createUpcomingRun();
        mockMvc.perform(delete("/api/admin/vehicle-runs/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/admin/vehicle-runs/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"x","version":0}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void admin_list_includes_past_runs() throws Exception {
        createRun(now().minusDays(7), "지난 주");
        createUpcomingRun();
        mockMvc.perform(get("/api/admin/vehicle-runs").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    // ---- 회원: 목록·신청·취소 ----

    @Test
    void list_anonymous_is_401() throws Exception {
        mockMvc.perform(get("/api/vehicle-runs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void list_user_without_apply_is_403() throws Exception {
        mockMvc.perform(get("/api/vehicle-runs").header("Authorization", userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void list_shows_upcoming_only_with_my_request() throws Exception {
        createRun(now().minusDays(7), "지난 주");
        long id = createUpcomingRun();

        mockMvc.perform(get("/api/vehicle-runs").header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].note").value("토요일 오후, 학원 앞 경유"))
                .andExpect(jsonPath("$.content[0].myRequest").isEmpty());

        mockMvc.perform(post("/api/vehicle-runs/" + id + "/requests")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupLocation":"OO아파트 정문","note":"동생 1명 동승"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pickupLocation").value("OO아파트 정문"));

        mockMvc.perform(get("/api/vehicle-runs").header("Authorization", memberToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].myRequest.pickupLocation").value("OO아파트 정문"))
                .andExpect(jsonPath("$.content[0].myRequest.note").value("동생 1명 동승"));
    }

    @Test
    void apply_twice_is_409() throws Exception {
        long id = createUpcomingRun();
        mockMvc.perform(post("/api/vehicle-runs/" + id + "/requests")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupLocation":"OO아파트 정문"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/vehicle-runs/" + id + "/requests")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupLocation":"학교 후문"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void apply_to_departed_run_is_400() throws Exception {
        long id = createRun(now().minusHours(1), "이미 출발");
        mockMvc.perform(post("/api/vehicle-runs/" + id + "/requests")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupLocation":"OO아파트 정문"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void apply_to_deleted_run_is_404() throws Exception {
        long id = createUpcomingRun();
        mockMvc.perform(delete("/api/admin/vehicle-runs/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/vehicle-runs/" + id + "/requests")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupLocation":"OO아파트 정문"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void apply_blank_pickup_location_is_400() throws Exception {
        long id = createUpcomingRun();
        mockMvc.perform(post("/api/vehicle-runs/" + id + "/requests")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupLocation":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void cancel_then_reapply_succeeds() throws Exception {
        long id = createUpcomingRun();
        mockMvc.perform(post("/api/vehicle-runs/" + id + "/requests")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupLocation":"OO아파트 정문"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/vehicle-runs/" + id + "/requests/me").header("Authorization", memberToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/vehicle-runs/" + id + "/requests")
                        .header("Authorization", memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupLocation":"학교 후문"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pickupLocation").value("학교 후문"));
    }

    @Test
    void cancel_without_request_is_404() throws Exception {
        long id = createUpcomingRun();
        mockMvc.perform(delete("/api/vehicle-runs/" + id + "/requests/me").header("Authorization", memberToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
