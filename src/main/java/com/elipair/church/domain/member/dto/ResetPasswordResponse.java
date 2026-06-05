package com.elipair.church.domain.member.dto;

/** 임시 비밀번호 평문 1회 반환(관리자가 교인에게 대면 전달). 로그·예외에 절대 남기지 않는다. */
public record ResetPasswordResponse(String temporaryPassword) {}
