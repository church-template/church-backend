package com.elipair.church.domain.challenge;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeReadingLogRepository extends JpaRepository<ChallengeReadingLog, Long> {

    Optional<ChallengeReadingLog> findByParticipationIdAndReadDate(Long participationId, LocalDate readDate);

    List<ChallengeReadingLog> findByParticipationIdAndReadDateBetweenOrderByReadDateAsc(
            Long participationId, LocalDate from, LocalDate to);

    /** 스트릭 계산용 날짜 목록 — 참여당 최대 챌린지 일수 행이라 전량 로드로 충분. */
    @Query("select l.readDate from ChallengeReadingLog l "
            + "where l.participationId = :participationId order by l.readDate desc")
    List<LocalDate> findReadDatesDesc(@Param("participationId") Long participationId);
}
