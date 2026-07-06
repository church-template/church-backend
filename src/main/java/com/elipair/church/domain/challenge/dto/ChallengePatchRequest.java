package com.elipair.church.domain.challenge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 챌린지 부분 수정(PATCH) 요청 — null 필드는 미변경. 구간·기간 필드는 참여자 존재 시 거부(설계 §3). */
public record ChallengePatchRequest(
        @Size(max = 100) String title,
        @Size(max = 50000) String description,
        @Min(1) @Max(66) Integer startBook,
        @Min(1) @Max(66) Integer endBook,
        LocalDate startDate,
        @Min(1) @Max(3650) Integer targetDays,
        @NotNull Long version) {}
