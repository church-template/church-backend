package com.elipair.church.domain.challenge;

import com.elipair.church.global.common.BaseEntity;
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
 * 통독 챌린지(설계 §2). 범위는 연속 권 구간 [startBook, endBook](1~66) — 신약=40~66 등.
 * 종료일·하루 목표·상태는 전부 파생값(저장 안 함). 수정가능 콘텐츠라 BaseEntity 상속.
 */
@Entity
@Table(name = "bible_challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BibleChallenge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_book", nullable = false)
    private int startBook;

    @Column(name = "end_book", nullable = false)
    private int endBook;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "target_days", nullable = false)
    private int targetDays;

    private BibleChallenge(
            String title, String description, int startBook, int endBook, LocalDate startDate, int targetDays) {
        this.title = title;
        this.description = description;
        this.startBook = startBook;
        this.endBook = endBook;
        this.startDate = startDate;
        this.targetDays = targetDays;
    }

    public static BibleChallenge create(
            String title, String description, int startBook, int endBook, LocalDate startDate, int targetDays) {
        return new BibleChallenge(title, description, startBook, endBook, startDate, targetDays);
    }

    /** PATCH 부분 수정 — null 인자는 미변경. 참여자 존재 시 구간·기간 가드는 서비스가 수행(설계 §3). */
    public void applyPatch(
            String title,
            String description,
            Integer startBook,
            Integer endBook,
            LocalDate startDate,
            Integer targetDays) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (startBook != null) {
            this.startBook = startBook;
        }
        if (endBook != null) {
            this.endBook = endBook;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (targetDays != null) {
            this.targetDays = targetDays;
        }
    }

    public int totalChapters() {
        return BibleStructure.chapterCount(startBook, endBook);
    }

    /** 종료일(포함) = start_date + target_days - 1. */
    public LocalDate endDate() {
        return startDate.plusDays(targetDays - 1L);
    }

    /** 하루 목표 = ⌈구간 장 수 / target_days⌉. */
    public int dailyGoal() {
        return Math.ceilDiv(totalChapters(), targetDays);
    }

    public ChallengeStatus status(LocalDate today) {
        return ChallengeStatus.of(startDate, endDate(), today);
    }
}
