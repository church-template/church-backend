package com.elipair.church.domain.member.dto;

import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberAuthorities;
import com.elipair.church.domain.role.Role;
import java.time.LocalDateTime;
import java.util.List;

public record MemberDetailResponse(
        String uuid,
        String name,
        String phone,
        String email,
        String position,
        List<String> roles,
        List<String> permissions,
        boolean approved,
        boolean termsAgreed,
        boolean privacyAgreed,
        LocalDateTime agreedAt,
        LocalDateTime createdAt) {

    public static MemberDetailResponse from(Member m) {
        return new MemberDetailResponse(
                m.getUuid().toString(),
                m.getName(),
                m.getPhone(),
                m.getEmail(),
                m.getPosition() == null ? null : m.getPosition().getName(),
                m.getRoles().stream().map(Role::getName).sorted().toList(),
                MemberAuthorities.permissions(m),
                MemberAuthorities.isApproved(m),
                m.isTermsAgreed(),
                m.isPrivacyAgreed(),
                m.getAgreedAt(),
                m.getCreatedAt());
    }
}
