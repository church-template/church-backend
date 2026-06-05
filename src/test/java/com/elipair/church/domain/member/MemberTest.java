package com.elipair.church.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elipair.church.domain.role.Role;
import com.elipair.church.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

class MemberTest {

    private Member signup() {
        return Member.create("01012345678", "홍길동", "{enc}", "a@b.com", null, true, true);
    }

    @Test
    void create_generates_uuid_and_stamps_agreement() {
        Member m = signup();
        assertThat(m.getUuid()).isNotNull();
        assertThat(m.isTermsAgreed()).isTrue();
        assertThat(m.isPrivacyAgreed()).isTrue();
        assertThat(m.getAgreedAt()).isNotNull();
    }

    @Test
    void create_rejects_when_required_agreement_missing() {
        assertThatThrownBy(() -> Member.create("01012345678", "홍길동", "{enc}", null, null, true, false))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Member.create("01012345678", "홍길동", "{enc}", null, null, false, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void update_profile_is_partial() {
        Member m = signup();
        m.updateProfile("새이름", null, null);
        assertThat(m.getName()).isEqualTo("새이름");
        assertThat(m.getPhone()).isEqualTo("01012345678"); // 미변경
    }

    @Test
    void reset_agreement_clears_only_target_and_keeps_agreed_at() {
        Member m = signup();
        var before = m.getAgreedAt();
        m.resetAgreement("terms");
        assertThat(m.isTermsAgreed()).isFalse();
        assertThat(m.isPrivacyAgreed()).isTrue();
        assertThat(m.getAgreedAt()).isEqualTo(before); // 리셋은 timestamp 불변
    }

    @Test
    void agree_sets_both_true_and_updates_agreed_at() {
        Member m = signup();
        m.resetAgreement("terms");
        m.agree();
        assertThat(m.isTermsAgreed()).isTrue();
        assertThat(m.isPrivacyAgreed()).isTrue();
    }

    @Test
    void grant_and_revoke_role_are_idempotent_membership_changes() {
        Member m = signup();
        Role member = Role.create("MEMBER", 100, "교인");
        assertThat(m.grantRole(member)).isTrue(); // 새로 추가
        assertThat(m.grantRole(member)).isFalse(); // 이미 보유
        assertThat(m.getRoles()).contains(member);
        assertThat(m.revokeRole(member)).isTrue(); // 제거
        assertThat(m.revokeRole(member)).isFalse(); // 미보유
    }
}
