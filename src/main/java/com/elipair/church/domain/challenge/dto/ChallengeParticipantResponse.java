package com.elipair.church.domain.challenge.dto;

/**
 * 참여자 진도 명단 한 줄(#68). 순위 숫자는 매기지 않는다 — 정렬만으로 진도순을 드러낸다.
 * progressRate·currentPosition은 my-progress와 같은 "현재 회독" 기준(두 화면 숫자 불일치 방지),
 * 정렬 키는 누적 총 장 수라 회독을 마쳐 포인터가 0인 참여자가 위에 온다. me = 본인 행(동명이인 방어).
 * currentPosition null = 현재 회독 시작 전. 이름 외 식별자는 노출하지 않는다.
 */
public record ChallengeParticipantResponse(
        String name,
        int chaptersRead,
        double progressRate,
        int roundsCompleted,
        BiblePositionResponse currentPosition,
        boolean me) {}
