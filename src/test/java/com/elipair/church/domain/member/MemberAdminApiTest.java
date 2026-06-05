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
class MemberAdminApiTest {

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

    private String memberManager() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(9L, java.util.UUID.randomUUID().toString(), "관리자", 900),
                        null,
                        List.of("MEMBER_MANAGE"));
    }

    private String plainUser() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(8L, java.util.UUID.randomUUID().toString(), "사용자", 0), null, List.of());
    }

    private Member persist(String phone, String name) {
        return memberRepository.saveAndFlush(Member.create(phone, name, "{enc}", null, null, true, true));
    }

    @Test
    void list_members_paginated_for_manager() throws Exception {
        persist("01011112222", "회원1");
        persist("01033334444", "회원2");

        mockMvc.perform(get("/api/members?page=0&size=10").header("Authorization", memberManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void list_members_without_permission_is_403() throws Exception {
        mockMvc.perform(get("/api/members").header("Authorization", plainUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    void detail_by_uuid_for_manager() throws Exception {
        Member m = persist("01055556666", "상세회원");

        mockMvc.perform(get("/api/members/" + m.getUuid()).header("Authorization", memberManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("상세회원"))
                .andExpect(jsonPath("$.phone").value("01055556666"));
    }

    @Test
    void detail_unknown_uuid_is_404() throws Exception {
        mockMvc.perform(get("/api/members/" + java.util.UUID.randomUUID()).header("Authorization", memberManager()))
                .andExpect(status().isNotFound());
    }

    @Test
    void admin_update_member_phone() throws Exception {
        Member m = persist("01011112222", "원본");

        mockMvc.perform(patch("/api/admin/members/" + m.getUuid())
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-9090-9090\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("01090909090"));
    }

    @Test
    void admin_reset_password_returns_temporary_once() throws Exception {
        Member m = persist("01022223333", "비번분실");

        mockMvc.perform(post("/api/admin/members/" + m.getUuid() + "/reset-password")
                        .header("Authorization", memberManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty());
    }
}
