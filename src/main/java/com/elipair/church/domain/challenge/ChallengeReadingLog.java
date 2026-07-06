package com.elipair.church.domain.challenge;

import com.elipair.church.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 날짜별 읽음 로그(설계 §2) — 하루 1행, 같은 날 추가 읽기는 chapters 누적.
 * 스트릭·히트맵·페이스가 전부 여기서 파생. 취소는 물리 삭제(소프트삭제·낙관락 불필요 → BaseTimeEntity만).
 */
@Entity
@Table(name = "challenge_reading_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeReadingLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participation_id", nullable = false)
    private Long participationId;

    @Column(name = "read_date", nullable = false)
    private LocalDate readDate;

    @Column(nullable = false)
    private int chapters;

    private ChallengeReadingLog(Long participationId, LocalDate readDate, int chapters) {
        this.participationId = participationId;
        this.readDate = readDate;
        this.chapters = chapters;
    }

    public static ChallengeReadingLog create(Long participationId, LocalDate readDate, int chapters) {
        return new ChallengeReadingLog(participationId, readDate, chapters);
    }

    /** 같은 날 추가 읽기 누적(설계 §3 read 3단계). */
    public void addChapters(int chapters) {
        this.chapters += chapters;
    }
}
