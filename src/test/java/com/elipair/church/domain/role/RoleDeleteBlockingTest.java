package com.elipair.church.domain.role;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleDeleteBlockingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MemberRepository memberRepository;

    @AfterEach
    void cleanup() {
        memberRepository.deleteAll(memberRepository.findAll());
        roleRepository.deleteAll(roleRepository.findAll().stream()
                .filter(r -> !List.of("SUPER_ADMIN", "ADMIN", "MEMBER", "USER").contains(r.getName()))
                .toList());
    }

    private String roleManager() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(1L, "uuid-admin", "관리자", 1000), null, List.of("ROLE_MANAGE"));
    }

    @Test
    void deleting_role_assigned_to_member_is_409_role_in_use() throws Exception {
        Role editor = roleRepository.saveAndFlush(Role.create("EDITOR", 500, "편집자"));
        Member m = Member.create("01012345678", "회원", "{enc}", null, null, true, true);
        m.grantRole(editor);
        memberRepository.saveAndFlush(m);

        mockMvc.perform(delete("/api/admin/roles/" + editor.getId()).header("Authorization", roleManager()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROLE_IN_USE"));
    }

    @Test
    void deleting_unassigned_role_succeeds() throws Exception {
        Role unused = roleRepository.saveAndFlush(Role.create("UNUSED", 400, "미사용"));

        mockMvc.perform(delete("/api/admin/roles/" + unused.getId()).header("Authorization", roleManager()))
                .andExpect(status().isNoContent());
    }
}
