package com.elipair.church.domain.challenge.dto;

import com.elipair.church.domain.challenge.BibleChallenge;
import com.elipair.church.domain.challenge.ChallengeStatus;
import java.time.LocalDate;

/** 목록 카드(설계 §3) — 본문(description) 제외(목록 카드 관례). endDate·totalChapters·dailyGoal·status는 파생. */
public record ChallengeCardResponse(
        Long id,
        String title,
        int startBook,
        int endBook,
        LocalDate startDate,
        LocalDate endDate,
        int targetDays,
        int totalChapters,
        int dailyGoal,
        ChallengeStatus status) {

    public static ChallengeCardResponse from(BibleChallenge c, LocalDate today) {
        return new ChallengeCardResponse(
                c.getId(),
                c.getTitle(),
                c.getStartBook(),
                c.getEndBook(),
                c.getStartDate(),
                c.endDate(),
                c.getTargetDays(),
                c.totalChapters(),
                c.dailyGoal(),
                c.status(today));
    }
}
