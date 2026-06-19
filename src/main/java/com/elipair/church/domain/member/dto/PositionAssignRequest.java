package com.elipair.church.domain.member.dto;

/** 회원 직분 지정/해제. positionId가 null이면 직분 해제(@NotNull 금지). */
public record PositionAssignRequest(Long positionId) {}
