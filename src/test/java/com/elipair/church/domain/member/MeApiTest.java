package com.elipair.church.domain.member;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class MeApiTest {

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

    private String tokenFor(Member m) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(m.getId(), m.getUuid().toString(), m.getName(), 100),
                        null,
                        List.of("GALLERY_VIEW"));
    }

    @Test
    void me_returns_db_latest_with_roles_and_permissions() throws Exception {
        Role member = role("MEMBER");
        Member m = Member.create("01012345678", "홍길동", "{enc}", "a@b.com", null, true, true);
        m.grantRole(member);
        memberRepository.saveAndFlush(m);

        mockMvc.perform(get("/api/members/me").header("Authorization", tokenFor(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
                // V16 이후 MEMBER = GALLERY_VIEW + CHALLENGE_PARTICIPATE + SERMON_VIEW + VEHICLE_APPLY, 순서는 계약이 아니라 전체
                // 집합으로 단언
                .andExpect(jsonPath(
                        "$.permissions",
                        containsInAnyOrder("GALLERY_VIEW", "CHALLENGE_PARTICIPATE", "SERMON_VIEW", "VEHICLE_APPLY")))
                .andExpect(jsonPath("$.approved").value(true)) // GALLERY_VIEW 보유 = 승인
                .andExpect(jsonPath("$.termsAgreed").value(true));
    }

    @Test
    void me_approved_false_for_user_without_gallery_view() throws Exception {
        Role user = role("USER"); // 권한 없음 = 미승인
        Member m = Member.create("01099998888", "미승인자", "{enc}", null, null, true, true);
        m.grantRole(user);
        memberRepository.saveAndFlush(m);

        mockMvc.perform(get("/api/members/me").header("Authorization", tokenFor(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false));
    }

    @Test
    void me_without_token_is_401() throws Exception {
        mockMvc.perform(get("/api/members/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void patch_me_updates_name_and_keeps_same_phone() throws Exception {
        Member m = Member.create("01012345678", "홍길동", "{enc}", null, null, true, true);
        memberRepository.saveAndFlush(m);

        mockMvc.perform(patch("/api/members/me")
                        .header("Authorization", tokenFor(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김길동\",\"phone\":\"010-1234-5678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김길동"))
                .andExpect(jsonPath("$.phone").value("01012345678")); // 동일 번호 유지 → 409 아님
    }

    @Test
    void patch_me_duplicate_phone_is_409() throws Exception {
        memberRepository.saveAndFlush(Member.create("01099998888", "타인", "{enc}", null, null, true, true));
        Member me = Member.create("01012345678", "홍길동", "{enc}", null, null, true, true);
        memberRepository.saveAndFlush(me);

        mockMvc.perform(patch("/api/members/me")
                        .header("Authorization", tokenFor(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-9999-8888\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void patch_me_short_password_is_400() throws Exception {
        Member m = Member.create("01012345678", "홍길동", "{enc}", null, null, true, true);
        memberRepository.saveAndFlush(m);

        mockMvc.perform(patch("/api/members/me")
                        .header("Authorization", tokenFor(m))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }
}
