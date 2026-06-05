package com.elipair.church.domain.auth.dto;

import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.role.Role;
import java.util.List;

public record MemberSummary(String uuid, String name, String phone, String position, List<String> roles) {

    public static MemberSummary from(Member m) {
        return new MemberSummary(
                m.getUuid().toString(),
                m.getName(),
                m.getPhone(),
                m.getPosition() == null ? null : m.getPosition().getName(),
                m.getRoles().stream().map(Role::getName).sorted().toList());
    }
}
