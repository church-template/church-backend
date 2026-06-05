package com.elipair.church.domain.member.dto;

import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.role.Role;
import java.time.LocalDateTime;
import java.util.List;

public record MemberCardResponse(
        String uuid,
        String name,
        String phone,
        String position,
        List<String> roles,
        boolean approved, // MEMBER 역할 보유 = 교인 승인 완료
        LocalDateTime createdAt) {

    public static MemberCardResponse from(Member m) {
        return new MemberCardResponse(
                m.getUuid().toString(),
                m.getName(),
                m.getPhone(),
                m.getPosition() == null ? null : m.getPosition().getName(),
                m.getRoles().stream().map(Role::getName).sorted().toList(),
                m.hasRole("MEMBER"),
                m.getCreatedAt());
    }
}
