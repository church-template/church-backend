package com.elipair.church.domain.auth.dto;

public record LoginResponse(TokenPair tokens, MemberSummary member, boolean requiresAgreement) {}
