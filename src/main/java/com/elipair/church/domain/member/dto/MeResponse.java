package com.elipair.church.domain.member.dto;

import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberAuthorities;
import com.elipair.church.domain.role.Role;
import java.time.LocalDateTime;
import java.util.List;

public record MeResponse(
        String uuid,
        String name,
        String phone,
        String email,
        String position, // 직분 한글 name(없으면 null)
        List<String> roles,
        List<String> permissions,
        int maxPriority,
        boolean approved, // GALLERY_VIEW 보유 = 승인(MemberCard/MemberDetail과 동일 규칙)
        boolean termsAgreed,
        boolean privacyAgreed,
        LocalDateTime agreedAt) {

    public static MeResponse from(Member m) {
        return new MeResponse(
                m.getUuid().toString(),
                m.getName(),
                m.getPhone(),
                m.getEmail(),
                m.getPosition() == null ? null : m.getPosition().getName(),
                m.getRoles().stream().map(Role::getName).sorted().toList(),
                MemberAuthorities.permissions(m),
                MemberAuthorities.maxPriority(m),
                MemberAuthorities.isApproved(m),
                m.isTermsAgreed(),
                m.isPrivacyAgreed(),
                m.getAgreedAt());
    }
}
