package com.elipair.church.domain.challenge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 챌린지 개설(POST) 요청(설계 §3). startBook<=endBook 교차 검증은 서비스가 수행. */
public record ChallengeCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 50000) String description,
        @NotNull @Min(1) @Max(66) Integer startBook,
        @NotNull @Min(1) @Max(66) Integer endBook,
        @NotNull LocalDate startDate,
        @NotNull @Min(1) @Max(3650) Integer targetDays) {}
