package com.elipair.church.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

/** 약관 일괄 리셋 대상: "terms" 또는 "privacy"(서비스에서 검증). */
public record AgreementResetRequest(@NotBlank String target) {}
