package com.elipair.church.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** 본인 프로필 부분 수정. 모든 필드 선택(null=미변경). password는 있으면 8자 이상(복잡도 강제 없음). */
public record MeUpdateRequest(
        @Size(max = 50) String name,
        @Size(max = 20) String phone,

        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다")
        String password,

        @Email @Size(max = 255) String email) {}
