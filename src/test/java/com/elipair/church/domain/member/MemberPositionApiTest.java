package com.elipair.church.domain.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.position.Position;
import com.elipair.church.domain.position.PositionRepository;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import java.util.List;
import java.util.UUID;
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
class MemberPositionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PositionRepository positionRepository;

    @AfterEach
    void cleanup() {
        memberRepository.deleteAll(memberRepository.findAll()); // 회원 먼저(position FK 참조 해소)
        positionRepository.deleteAll();
    }

    private String memberManager() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(9L, UUID.randomUUID().toString(), "관리자", 900),
                        null,
                        List.of("MEMBER_MANAGE"));
    }

    private String plainUser() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(8L, UUID.randomUUID().toString(), "사용자", 0), null, List.of());
    }

    private Member persist(String phone, String name) {
        return memberRepository.saveAndFlush(Member.create(phone, name, "{enc}", null, null, true, true));
    }

    private Long positionId(String name, int sortOrder) {
        return positionRepository.saveAndFlush(Position.of(name, sortOrder)).getId();
    }

    @Test
    void assign_position_returns_detail_with_position() throws Exception {
        Member target = persist("01055556666", "직분대상");
        Long deacon = positionId("집사", 5);

        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + deacon + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("집사"));
    }

    @Test
    void clear_position_with_null_returns_detail_without_position() throws Exception {
        Member target = persist("01066667777", "해제대상");
        Long elder = positionId("장로", 3);
        // 먼저 직분 부여
        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + elder + "}"))
                .andExpect(status().isOk());
        // null로 해제
        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void assign_nonexistent_position_is_404() throws Exception {
        Member target = persist("01077778888", "대상");

        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void assign_position_to_nonexistent_member_is_404() throws Exception {
        Long deacon = positionId("집사", 5);

        mockMvc.perform(put("/api/admin/members/" + UUID.randomUUID() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + deacon + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void assign_position_without_member_manage_is_403() throws Exception {
        Member target = persist("01088889999", "대상");
        Long deacon = positionId("집사", 5);

        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", plainUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + deacon + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void assign_own_position_is_allowed() throws Exception {
        // 직분은 권한과 무관하므로 자기 자신에게도 지정 가능(self-protection 없음).
        Member self = persist("01099990000", "본인");
        Long pastor = positionId("목사", 1);

        mockMvc.perform(put("/api/admin/members/" + self.getUuid() + "/position")
                        .header("Authorization",
                                "Bearer " + provider.issueAccess(
                                        new MemberPrincipal(self.getId(), self.getUuid().toString(), "본인", 900),
                                        null, List.of("MEMBER_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + pastor + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("목사"));
    }

    @Test
    void reassign_position_to_different_position_returns_new_position() throws Exception {
        Member target = persist("01012121212", "재지정대상");
        Long deacon = positionId("집사", 5);
        Long elder = positionId("장로", 3);

        // 집사 부여
        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + deacon + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("집사"));

        // 장로로 변경
        mockMvc.perform(put("/api/admin/members/" + target.getUuid() + "/position")
                        .header("Authorization", memberManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + elder + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("장로"));
    }
}
