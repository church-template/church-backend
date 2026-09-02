package com.elipair.church.domain.challenge;

/** 참여자 명단 조회용 프로젝션(#68) — 회원 이름 + 진도 포인터. 진도율·현재 위치는 서비스가 파생한다. */
public interface ChallengeParticipantRow {
    Long getMemberId();

    String getName();

    int getChaptersRead();

    int getRoundsCompleted();
}
