package com.elipair.church.domain.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
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
class MemberRoleApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RoleRepository roleRepository;

    @AfterEach
    void cleanup() {
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private Role role(String name) {
        return roleRepository.findAll().stream()
                .filter(r -> r.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private String roleManager(long requesterId, int maxPriority) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(
                                requesterId, java.util.UUID.randomUUID().toString(), "관리자", maxPriority),
                        null,
                        List.of("ROLE_MANAGE"));
    }

    private Member persist(String phone, String name) {
        return memberRepository.saveAndFlush(Member.create(phone, name, "{enc}", null, null, true, true));
    }

    @Test
    void grant_member_role_approves_church_member() throws Exception {
        Member target = persist("01011112222", "신규교인");
        Role member = role("MEMBER");

        mockMvc.perform(post("/api/admin/members/" + target.getUuid() + "/roles")
                        .header("Authorization", roleManager(999L, 900))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + member.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.hasItem("MEMBER")));
    }

    @Test
    void grant_role_above_requester_priority_is_403() throws Exception {
        Member target = persist("01033334444", "대상");
        Role admin = role("ADMIN"); // priority 900

        mockMvc.perform(post("/api/admin/members/" + target.getUuid() + "/roles")
                        .header("Authorization", roleManager(999L, 100)) // 요청자 maxPriority 100 < 900
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + admin.getId() + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void admin_cannot_grant_peer_admin_role() throws Exception {
        // 동급 위임 차단: ADMIN(900)이 ADMIN(900) 역할을 위임할 수 없다(슈퍼어드민만 어드민을 위임/박탈).
        Member target = persist("01099990000", "승급대상");
        Role admin = role("ADMIN"); // priority 900

        mockMvc.perform(post("/api/admin/members/" + target.getUuid() + "/roles")
                        .header("Authorization", roleManager(999L, 900)) // 요청자 maxPriority 900 == 대상 900
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + admin.getId() + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void super_admin_can_grant_admin_role() throws Exception {
        // 엄격히 하위 위임 허용: SUPER_ADMIN(1000)은 ADMIN(900)을 위임할 수 있다.
        Member target = persist("01012340000", "어드민될사람");
        Role admin = role("ADMIN"); // priority 900

        mockMvc.perform(post("/api/admin/members/" + target.getUuid() + "/roles")
                        .header("Authorization", roleManager(999L, 1000)) // 요청자 maxPriority 1000 > 900
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + admin.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.hasItem("ADMIN")));
    }

    @Test
    void changing_own_role_is_403() throws Exception {
        Member self = persist("01055556666", "본인");
        Role member = role("MEMBER");

        mockMvc.perform(post("/api/admin/members/" + self.getUuid() + "/roles")
                        .header("Authorization", roleManager(self.getId(), 900)) // 요청자 == 대상
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + member.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void super_admin_can_revoke_admin_role() throws Exception {
        // 강등(박탈): SUPER_ADMIN(1000)은 ADMIN(900)을 보유한 회원의 ADMIN 역할을 박탈할 수 있다.
        Member target = persist("01023450000", "강등될어드민");
        Role admin = role("ADMIN"); // priority 900
        target.grantRole(admin);
        memberRepository.saveAndFlush(target);

        mockMvc.perform(delete("/api/admin/members/" + target.getUuid() + "/roles/" + admin.getId())
                        .header("Authorization", roleManager(999L, 1000)))
                .andExpect(status().isNoContent());
    }

    @Test
    void admin_cannot_revoke_peer_admin_role() throws Exception {
        // 동급 차단: ADMIN(900)은 다른 ADMIN(900)의 역할을 박탈할 수 없다.
        Member target = persist("01034560000", "동급어드민");
        Role admin = role("ADMIN"); // priority 900
        target.grantRole(admin);
        memberRepository.saveAndFlush(target);

        mockMvc.perform(delete("/api/admin/members/" + target.getUuid() + "/roles/" + admin.getId())
                        .header("Authorization", roleManager(999L, 900)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void revoke_last_super_admin_is_403() throws Exception {
        Member onlyAdmin = persist("01077778888", "유일한최고관리자");
        Role superAdmin = role("SUPER_ADMIN");
        onlyAdmin.grantRole(superAdmin);
        memberRepository.saveAndFlush(onlyAdmin);

        mockMvc.perform(delete("/api/admin/members/" + onlyAdmin.getUuid() + "/roles/" + superAdmin.getId())
                        .header("Authorization", roleManager(999L, 1000)))
                .andExpect(status().isForbidden());
    }

    @Test
    void revoke_role_returns_204() throws Exception {
        Member target = persist("01088889999", "회수대상");
        Role member = role("MEMBER");
        target.grantRole(member);
        memberRepository.saveAndFlush(target);

        mockMvc.perform(delete("/api/admin/members/" + target.getUuid() + "/roles/" + member.getId())
                        .header("Authorization", roleManager(999L, 900)))
                .andExpect(status().isNoContent());
    }
}
