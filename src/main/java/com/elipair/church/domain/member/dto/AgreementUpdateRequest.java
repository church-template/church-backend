package com.elipair.church.domain.member.dto;

/** 재동의 제출. 필수 2종 모두 true여야 성립(서비스에서 검증). */
public record AgreementUpdateRequest(boolean termsAgreed, boolean privacyAgreed) {}
