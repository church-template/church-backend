package com.elipair.church.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** 관리자 회원 정보 수정(번호 갱신 구제 등). 비밀번호는 reset-password로만 변경. */
public record AdminMemberUpdateRequest(
        @Size(max = 50) String name,
        @Size(max = 20) String phone,
        @Email @Size(max = 255) String email) {}
