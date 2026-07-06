package com.elipair.church.domain.challenge.dto;

import com.elipair.church.domain.challenge.BibleChallenge;
import com.elipair.church.domain.challenge.ChallengeStatus;
import java.time.LocalDate;

/** 진행 응답에 내장되는 챌린지 요약(설계 §3 my-progress / my-participations). */
public record ChallengeSummaryResponse(
        Long id, String title, LocalDate startDate, LocalDate endDate, ChallengeStatus status, int totalChapters) {

    public static ChallengeSummaryResponse from(BibleChallenge c, LocalDate today) {
        return new ChallengeSummaryResponse(
                c.getId(), c.getTitle(), c.getStartDate(), c.endDate(), c.status(today), c.totalChapters());
    }
}
