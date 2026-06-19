package com.elipair.church.domain.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.domain.role.Permission;
import com.elipair.church.domain.role.Role;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemberAuthoritiesTest {

    @Test
    void flattens_distinct_permissions_and_max_priority() {
        Role admin = Role.create("ADMIN", 900, "관리자");
        admin.replacePermissions(List.of(Permission.of("MEMBER_MANAGE", "회원"), Permission.of("ROLE_MANAGE", "역할")));
        Role member = Role.create("MEMBER", 100, "교인");
        member.replacePermissions(List.of(Permission.of("GALLERY_VIEW", "갤러리")));

        Member m = Member.create("01012345678", "홍길동", "{enc}", null, null, true, true);
        m.grantRole(admin);
        m.grantRole(member);

        assertThat(MemberAuthorities.permissions(m))
                .containsExactly("GALLERY_VIEW", "MEMBER_MANAGE", "ROLE_MANAGE"); // distinct+정렬
        assertThat(MemberAuthorities.maxPriority(m)).isEqualTo(900);
    }

    @Test
    void approved_for_super_admin_without_member_role() {
        // 슈퍼어드민은 MEMBER 역할 없이도 GALLERY_VIEW(전 권한)를 보유 → 승인 상태로 본다.
        Role superAdmin = Role.create("SUPER_ADMIN", 1000, "최고관리자");
        superAdmin.replacePermissions(
                List.of(Permission.of("GALLERY_VIEW", "갤러리"), Permission.of("ROLE_MANAGE", "역할")));
        Member m = Member.create("01012345678", "최고관리자", "{enc}", null, null, true, true);
        m.grantRole(superAdmin);

        assertThat(MemberAuthorities.isApproved(m)).isTrue();
    }

    @Test
    void approved_for_member_with_gallery_view() {
        Role member = Role.create("MEMBER", 100, "교인");
        member.replacePermissions(List.of(Permission.of("GALLERY_VIEW", "갤러리")));
        Member m = Member.create("01012345678", "교인", "{enc}", null, null, true, true);
        m.grantRole(member);

        assertThat(MemberAuthorities.isApproved(m)).isTrue();
    }

    @Test
    void not_approved_for_plain_user_without_gallery_view() {
        Role user = Role.create("USER", 0, "일반사용자"); // 권한 없음
        Member m = Member.create("01012345678", "가입자", "{enc}", null, null, true, true);
        m.grantRole(user);

        assertThat(MemberAuthorities.isApproved(m)).isFalse();
    }

    @Test
    void empty_roles_yield_no_permissions_and_zero_priority() {
        Member m = Member.create("01012345678", "홍길동", "{enc}", null, null, true, true);
        assertThat(MemberAuthorities.permissions(m)).isEmpty();
        assertThat(MemberAuthorities.maxPriority(m)).isZero();
    }
}
