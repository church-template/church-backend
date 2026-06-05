package com.elipair.church.domain.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
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
class AgreementApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private MemberRepository memberRepository;

    @AfterEach
    void cleanup() {
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String tokenFor(Member m) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(m.getId(), m.getUuid().toString(), m.getName(), 0), null, List.of());
    }

    @Test
    void get_agreements_returns_flags() throws Exception {
        Member m = Member.create("01012345678", "홍길동", "{enc}", null, null, true, true);
        m.resetAgreement("terms");
        memberRepository.saveAndFlush(m);

        mockMvc.perform(get("/api/members/me/agreements").header("Authorization", tokenFor(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsAgreed").value(false))
                .andExpect(jsonPath("$.privacyAgreed").value(true));
    }

    @Test
    void patch_agreements_reconsent_sets_both_true() throws Exception {
        Member m = Member.create("01012345678", "홍길동", "{enc}", null, null, true, true);
        m.resetAgreement("terms");
        memberRepository.saveAndFlush(m);

        mockMvc.perform(patch("/api/members/me/agreements")
                        .header("Authorization", tokenFor(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsAgreed\":true,\"privacyAgreed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsAgreed").value(true));
    }

    @Test
    void patch_agreements_without_both_true_is_400() throws Exception {
        Member m = Member.create("01012345678", "홍길동", "{enc}", null, null, true, true);
        memberRepository.saveAndFlush(m);

        mockMvc.perform(patch("/api/members/me/agreements")
                        .header("Authorization", tokenFor(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termsAgreed\":true,\"privacyAgreed\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void admin_reset_terms_flips_all_active_members() throws Exception {
        Member a = memberRepository.saveAndFlush(Member.create("01011112222", "회원A", "{enc}", null, null, true, true));
        memberRepository.saveAndFlush(Member.create("01033334444", "회원B", "{enc}", null, null, true, true));

        java.time.LocalDateTime agreedBefore = memberRepository
                .findByPhoneAndDeletedAtIsNull("01011112222")
                .orElseThrow()
                .getAgreedAt();

        String adminToken = "Bearer "
                + provider.issueAccess(
                        new com.elipair.church.global.security.MemberPrincipal(99L, "uuid-admin", "관리자", 900),
                        null,
                        java.util.List.of("MEMBER_MANAGE"));

        mockMvc.perform(post("/api/admin/agreements/reset")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"terms\"}"))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(memberRepository
                        .findByPhoneAndDeletedAtIsNull("01011112222")
                        .orElseThrow()
                        .isTermsAgreed())
                .isFalse();

        var reloaded =
                memberRepository.findByPhoneAndDeletedAtIsNull("01011112222").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(reloaded.getAgreedAt()).isEqualTo(agreedBefore);
        org.assertj.core.api.Assertions.assertThat(reloaded.getAgreedAt()).isNotNull();
    }

    @Test
    void admin_reset_invalid_target_is_400() throws Exception {
        String adminToken = "Bearer "
                + provider.issueAccess(
                        new com.elipair.church.global.security.MemberPrincipal(99L, "uuid-admin", "관리자", 900),
                        null,
                        java.util.List.of("MEMBER_MANAGE"));

        mockMvc.perform(post("/api/admin/agreements/reset")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"unknown\"}"))
                .andExpect(status().isBadRequest());
    }
}
