package com.elipair.church.domain.challenge.dto;

import com.elipair.church.domain.challenge.BibleChallenge;
import com.elipair.church.domain.challenge.ChallengeStatus;
import java.time.LocalDate;

/** 챌린지 상세(설계 §3). joined = 요청 회원의 참여 여부(회원 상세 조회에서만 의미 — 관리자 응답은 false 고정). */
public record ChallengeDetailResponse(
        Long id,
        String title,
        String description,
        int startBook,
        int endBook,
        LocalDate startDate,
        LocalDate endDate,
        int targetDays,
        int totalChapters,
        int dailyGoal,
        ChallengeStatus status,
        boolean joined,
        Long version) {

    public static ChallengeDetailResponse from(BibleChallenge c, LocalDate today, boolean joined) {
        return new ChallengeDetailResponse(
                c.getId(),
                c.getTitle(),
                c.getDescription(),
                c.getStartBook(),
                c.getEndBook(),
                c.getStartDate(),
                c.endDate(),
                c.getTargetDays(),
                c.totalChapters(),
                c.dailyGoal(),
                c.status(today),
                joined,
                c.getVersion());
    }
}
