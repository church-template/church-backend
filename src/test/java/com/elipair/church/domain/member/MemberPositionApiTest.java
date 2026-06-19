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
}
