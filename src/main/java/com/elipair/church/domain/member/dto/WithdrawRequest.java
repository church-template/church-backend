package com.elipair.church.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

/** 자가탈퇴 요청. 현재 비밀번호로 재인증. */
public record WithdrawRequest(@NotBlank String password) {}
