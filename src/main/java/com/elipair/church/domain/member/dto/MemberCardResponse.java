package com.elipair.church.domain.member.dto;

import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberAuthorities;
import com.elipair.church.domain.role.Role;
import java.time.LocalDateTime;
import java.util.List;

public record MemberCardResponse(
        String uuid,
        String name,
        String phone,
        String position,
        List<String> roles,
        boolean approved, // GALLERY_VIEW 권한 보유 = 승인 상태(교인 + 권한 보유 어드민 포함)
        LocalDateTime createdAt) {

    public static MemberCardResponse from(Member m) {
        return new MemberCardResponse(
                m.getUuid().toString(),
                m.getName(),
                m.getPhone(),
                m.getPosition() == null ? null : m.getPosition().getName(),
                m.getRoles().stream().map(Role::getName).sorted().toList(),
                MemberAuthorities.isApproved(m),
                m.getCreatedAt());
    }
}
