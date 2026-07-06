package com.elipair.church.domain.challenge.dto;

import java.time.LocalDate;

/** 마이페이지 참여 이력 1건(설계 §3 my-participations). completed = 회독 1회 이상. */
public record MyParticipationResponse(
        ChallengeSummaryResponse challenge,
        LocalDate joinedAt,
        double progressRate,
        int chaptersRead,
        int roundsCompleted,
        boolean completed,
        int streakDays) {}
