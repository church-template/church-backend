package com.elipair.church.domain.visibility.dto;

import com.elipair.church.domain.visibility.VisibilityLevel;
import jakarta.validation.constraints.NotNull;

/** 5개 값 전체 교체. 하나라도 빠지면 400 — 일부만 보내 나머지가 기본값으로 바뀌는 사고를 막는다. */
public record VisibilityUpdateRequest(
        @NotNull VisibilityLevel notices,
        @NotNull VisibilityLevel events,
        @NotNull VisibilityLevel bulletins,
        @NotNull VisibilityLevel sermons,
        @NotNull VisibilityLevel gallery) {}
