package com.elipair.church.domain.challenge;

import java.time.LocalDate;

/** 챌린지 파생 상태 — 날짜에서 계산, 컬럼 아님(설계 §1: 종료 조작 없음). */
public enum ChallengeStatus {
    UPCOMING,
    ONGOING,
    ENDED;

    public static ChallengeStatus of(LocalDate startDate, LocalDate endDate, LocalDate today) {
        if (today.isBefore(startDate)) {
            return UPCOMING;
        }
        if (today.isAfter(endDate)) {
            return ENDED;
        }
        return ONGOING;
    }
}
