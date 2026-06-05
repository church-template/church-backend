package com.elipair.church.domain.auth.dto;

import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.role.Role;
import java.util.List;

public record SignupResponse(String uuid, String name, String phone, List<String> roles) {

    public static SignupResponse from(Member m) {
        return new SignupResponse(
                m.getUuid().toString(),
                m.getName(),
                m.getPhone(),
                m.getRoles().stream().map(Role::getName).sorted().toList());
    }
}
