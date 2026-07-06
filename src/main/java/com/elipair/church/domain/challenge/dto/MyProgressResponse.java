package com.elipair.church.domain.challenge.dto;

/**
 * 대시보드 원샷 응답(설계 §3 my-progress) — UI가 한 번의 호출로 전부 그린다.
 * currentPosition null = 현재 회독 시작 전(포인터 0). paceDays null = 기간 종료(ENDED).
 */
public record MyProgressResponse(
        double progressRate,
        BiblePositionResponse currentPosition,
        int chaptersRead,
        int totalChapters,
        int todayChapters,
        int dailyGoal,
        boolean todayDone,
        int streakDays,
        int roundsCompleted,
        Integer paceDays,
        ChallengeSummaryResponse challenge) {}
