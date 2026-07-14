package com.elipair.church.domain.inquiry;

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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InquiryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StringRedisTemplate redis;

    private Long adminId;

    @BeforeEach
    void seedAdmin() {
        Member admin =
                memberRepository.saveAndFlush(Member.create("01000000000", "관리자", "{enc}", null, null, true, true));
        adminId = admin.getId();
        clearRateLimit();
    }

    @AfterEach
    void cleanup() {
        inquiryRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
        clearRateLimit();
    }

    private void clearRateLimit() {
        Set<String> keys = redis.keys("inquiry:rl:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private String token(String permission) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(adminId, "uuid-" + adminId, "관리자", 900), null, List.of(permission));
    }

    private String adminToken() {
        return token("INQUIRY_MANAGE");
    }

    private static final String CREATE_BODY = """
            {"name":"김성도","phone":"010-1234-5678","email":"saint@example.com",
             "content":"주일 예배 시간이 궁금합니다","privacyAgreed":true}
            """;

    /** 레이트리밋(IP당 시간당 5건)에 걸리지 않도록 테스트마다 다른 IP로 제출한다. */
    private long createInquiry(String body, String ip) throws Exception {
        String json = mockMvc.perform(post("/api/inquiries")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // ---------- 공개 등록 ----------

    @Test
    void anonymous_can_submit_inquiry() throws Exception {
        mockMvc.perform(post("/api/inquiries")
                        .header("X-Forwarded-For", "10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void email_is_optional() throws Exception {
        String body = """
                {"name":"김성도","phone":"01012345678","content":"교회 주차장이 있는지 궁금합니다","privacyAgreed":true}
                """;
        mockMvc.perform(post("/api/inquiries")
                        .header("X-Forwarded-For", "10.0.0.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void content_shorter_than_ten_chars_is_400() throws Exception {
        String body = """
                {"name":"김성도","phone":"01012345678","content":"짧은문의","privacyAgreed":true}
                """;
        mockMvc.perform(post("/api/inquiries")
                        .header("X-Forwarded-For", "10.0.0.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void missing_privacy_agreement_is_400() throws Exception {
        String body = """
                {"name":"김성도","phone":"01012345678","content":"주일 예배 시간이 궁금합니다","privacyAgreed":false}
                """;
        mockMvc.perform(post("/api/inquiries")
                        .header("X-Forwarded-For", "10.0.0.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void missing_name_or_phone_is_400() throws Exception {
        String noName = """
                {"phone":"01012345678","content":"주일 예배 시간이 궁금합니다","privacyAgreed":true}
                """;
        mockMvc.perform(post("/api/inquiries")
                        .header("X-Forwarded-For", "10.0.0.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noName))
                .andExpect(status().isBadRequest());

        String noPhone = """
                {"name":"김성도","content":"주일 예배 시간이 궁금합니다","privacyAgreed":true}
                """;
        mockMvc.perform(post("/api/inquiries")
                        .header("X-Forwarded-For", "10.0.0.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noPhone))
                .andExpect(status().isBadRequest());
    }

    @Test
    void phone_is_stored_digits_only() throws Exception {
        long id = createInquiry(CREATE_BODY, "10.0.0.6");

        mockMvc.perform(get("/api/admin/inquiries/{id}", id).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("01012345678"));
    }

    @Test
    void sixth_submission_from_same_ip_within_an_hour_is_429() throws Exception {
        for (int i = 0; i < 5; i++) {
            createInquiry(CREATE_BODY, "10.0.0.7");
        }
        mockMvc.perform(post("/api/inquiries")
                        .header("X-Forwarded-For", "10.0.0.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    // ---------- 관리자 조회 ----------

    @Test
    void listing_inquiries_requires_inquiry_manage_permission() throws Exception {
        mockMvc.perform(get("/api/admin/inquiries")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/inquiries").header("Authorization", token("NOTICE_WRITE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/admin/inquiries").header("Authorization", adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void list_returns_cards_without_content_and_detail_includes_it() throws Exception {
        long id = createInquiry(CREATE_BODY, "10.0.0.8");

        mockMvc.perform(get("/api/admin/inquiries").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value((int) id))
                .andExpect(jsonPath("$.content[0].name").value("김성도"))
                .andExpect(jsonPath("$.content[0].completed").value(false))
                .andExpect(jsonPath("$.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/api/admin/inquiries/{id}", id).header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("주일 예배 시간이 궁금합니다"))
                .andExpect(jsonPath("$.email").value("saint@example.com"));
    }

    @Test
    void detail_of_unknown_id_is_404() throws Exception {
        mockMvc.perform(get("/api/admin/inquiries/{id}", 999999L).header("Authorization", adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // ---------- 완료 처리 ----------

    @Test
    void complete_toggles_completed_at() throws Exception {
        long id = createInquiry(CREATE_BODY, "10.0.0.9");

        mockMvc.perform(patch("/api/admin/inquiries/{id}/complete", id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mockMvc.perform(patch("/api/admin/inquiries/{id}/complete", id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").isEmpty());
    }

    @Test
    void completed_filter_splits_pending_and_done() throws Exception {
        long done = createInquiry(CREATE_BODY, "10.0.0.10");
        long pending = createInquiry(CREATE_BODY, "10.0.0.11");

        mockMvc.perform(patch("/api/admin/inquiries/{id}/complete", done)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/inquiries?completed=false").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value((int) pending));

        mockMvc.perform(get("/api/admin/inquiries?completed=true").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value((int) done));
    }

    // ---------- 삭제 ----------

    @Test
    void delete_soft_deletes_and_removes_from_list() throws Exception {
        long id = createInquiry(CREATE_BODY, "10.0.0.12");

        mockMvc.perform(delete("/api/admin/inquiries/{id}", id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/inquiries").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        mockMvc.perform(get("/api/admin/inquiries/{id}", id).header("Authorization", adminToken()))
                .andExpect(status().isNotFound());
    }
}
