package com.elipair.church.domain.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.challenge.dto.ChallengeReadRequest;
import com.elipair.church.domain.challenge.dto.MyProgressResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChallengeProgressServiceTest {

    /** 고정 "오늘" = 2026-07-06 (KST). */
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 6);

    private BibleChallengeRepository challengeRepository;
    private ChallengeParticipationRepository participationRepository;
    private ChallengeReadingLogRepository logRepository;
    private ChallengeProgressService service;

    @BeforeEach
    void init() {
        challengeRepository = mock(BibleChallengeRepository.class);
        participationRepository = mock(ChallengeParticipationRepository.class);
        logRepository = mock(ChallengeReadingLogRepository.class);
        service = new ChallengeProgressService(challengeRepository, participationRepository, logRepository, FIXED);
        when(logRepository.findReadDatesDesc(any())).thenReturn(List.of());
        when(logRepository.findByParticipationIdAndReadDate(any(), any())).thenReturn(Optional.empty());
    }

    /** 신약 60일: 6/27 시작(오늘 10일차), 260장, 하루 목표 5. */
    private BibleChallenge ntChallenge() {
        return BibleChallenge.create("신약 60일", null, 40, 66, LocalDate.of(2026, 6, 27), 60);
    }

    private ChallengeParticipation stubJoined(BibleChallenge challenge) {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        when(challengeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(challenge));
        when(participationRepository.findByChallengeIdAndMemberIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(Optional.of(p));
        return p;
    }

    // ---- join ----

    @Test
    void join_duplicate_throws_409() {
        when(challengeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ntChallenge()));
        when(participationRepository.existsByChallengeIdAndMemberIdAndDeletedAtIsNull(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.join(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
        verify(participationRepository, never()).save(any());
    }

    @Test
    void join_unknown_challenge_throws_404() {
        when(challengeRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.join(9L, 2L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ---- read: 기본값·누적·검증 ----

    @Test
    void read_without_chapters_fills_remaining_daily_goal() {
        ChallengeParticipation p = stubJoined(ntChallenge());

        MyProgressResponse res = service.read(1L, 2L, new ChallengeReadRequest(null, null));

        assertThat(res.chaptersRead()).isEqualTo(5); // dailyGoal 5
        assertThat(res.todayChapters()).isEqualTo(5);
        assertThat(res.todayDone()).isTrue();
        verify(logRepository).save(any(ChallengeReadingLog.class));
        assertThat(p.getChaptersRead()).isEqualTo(5);
    }

    @Test
    void read_accumulates_on_same_day_log() {
        stubJoined(ntChallenge());
        ChallengeReadingLog existing = ChallengeReadingLog.create(null, TODAY, 5);
        when(logRepository.findByParticipationIdAndReadDate(any(), eq(TODAY))).thenReturn(Optional.of(existing));

        service.read(1L, 2L, new ChallengeReadRequest(3, null));

        assertThat(existing.getChapters()).isEqualTo(8);
        verify(logRepository, never()).save(any());
    }

    @Test
    void read_default_after_goal_met_throws_400() {
        stubJoined(ntChallenge());
        when(logRepository.findByParticipationIdAndReadDate(any(), eq(TODAY)))
                .thenReturn(Optional.of(ChallengeReadingLog.create(null, TODAY, 5)));

        assertThatThrownBy(() -> service.read(1L, 2L, new ChallengeReadRequest(null, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void read_backfill_past_date_within_challenge_is_ok() {
        ChallengeParticipation p = stubJoined(ntChallenge());
        LocalDate backfill = LocalDate.of(2026, 7, 4);

        service.read(1L, 2L, new ChallengeReadRequest(5, backfill));

        assertThat(p.getChaptersRead()).isEqualTo(5);
        verify(logRepository).save(any(ChallengeReadingLog.class));
    }

    @Test
    void read_before_start_or_future_throws_400() {
        stubJoined(ntChallenge());

        assertThatThrownBy(() -> service.read(1L, 2L, new ChallengeReadRequest(5, LocalDate.of(2026, 6, 26))))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.read(1L, 2L, new ChallengeReadRequest(5, TODAY.plusDays(1))))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void read_not_joined_throws_404() {
        when(challengeRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ntChallenge()));
        when(participationRepository.findByChallengeIdAndMemberIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.read(1L, 2L, new ChallengeReadRequest(5, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ---- read: 회독 이월 / 위치 ----

    @Test
    void read_rollover_completes_round_and_resets_position() {
        // 오바댜(31권, 1장)만 — 구간 1장이라 3장 읽으면 3회독
        BibleChallenge tiny = BibleChallenge.create("오바댜 반복", null, 31, 31, LocalDate.of(2026, 7, 1), 30);
        stubJoined(tiny);

        MyProgressResponse res = service.read(1L, 2L, new ChallengeReadRequest(3, null));

        assertThat(res.roundsCompleted()).isEqualTo(3);
        assertThat(res.chaptersRead()).isZero();
        assertThat(res.currentPosition()).isNull(); // 새 회독 시작 전
        assertThat(res.progressRate()).isZero();
    }

    @Test
    void progress_current_position_crosses_book_boundary() {
        BibleChallenge full = BibleChallenge.create("전체 100일", null, 1, 66, LocalDate.of(2026, 6, 27), 100);
        ChallengeParticipation p = stubJoined(full);
        p.advance(57, 1189); // 창세기 50 + 출애굽기 7

        MyProgressResponse res = service.myProgress(1L, 2L);

        assertThat(res.currentPosition().book()).isEqualTo("출애굽기");
        assertThat(res.currentPosition().chapter()).isEqualTo(7);
    }

    // ---- cancel ----

    @Test
    void cancel_rolls_back_pointer_and_deletes_log() {
        ChallengeParticipation p = stubJoined(ntChallenge());
        p.advance(8, 260);
        ChallengeReadingLog log = ChallengeReadingLog.create(null, TODAY, 8);
        when(logRepository.findByParticipationIdAndReadDate(any(), eq(TODAY))).thenReturn(Optional.of(log));

        MyProgressResponse res = service.cancelRead(1L, 2L, null);

        assertThat(res.chaptersRead()).isZero();
        verify(logRepository).delete(log);
    }

    @Test
    void cancel_without_log_throws_404() {
        stubJoined(ntChallenge());

        assertThatThrownBy(() -> service.cancelRead(1L, 2L, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ---- streak / pace ----

    @Test
    void streak_counts_from_yesterday_when_today_not_logged() {
        stubJoined(ntChallenge());
        when(logRepository.findReadDatesDesc(any()))
                .thenReturn(List.of(TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3)));

        assertThat(service.myProgress(1L, 2L).streakDays()).isEqualTo(3);
    }

    @Test
    void streak_includes_today_when_logged() {
        stubJoined(ntChallenge());
        when(logRepository.findReadDatesDesc(any())).thenReturn(List.of(TODAY, TODAY.minusDays(1)));

        assertThat(service.myProgress(1L, 2L).streakDays()).isEqualTo(2);
    }

    @Test
    void streak_zero_when_gap_before_yesterday() {
        stubJoined(ntChallenge());
        when(logRepository.findReadDatesDesc(any())).thenReturn(List.of(TODAY.minusDays(3)));

        assertThat(service.myProgress(1L, 2L).streakDays()).isZero();
    }

    @Test
    void pace_negative_when_behind_schedule() {
        // 10일차, 하루 5장 → 예정 50장. 실제 30장 → -20/5 = -4일
        ChallengeParticipation p = stubJoined(ntChallenge());
        p.advance(30, 260);

        assertThat(service.myProgress(1L, 2L).paceDays()).isEqualTo(-4);
    }

    @Test
    void pace_null_when_ended() {
        BibleChallenge ended = BibleChallenge.create("끝난 챌린지", null, 40, 66, LocalDate.of(2026, 1, 1), 60);
        stubJoined(ended);

        assertThat(service.myProgress(1L, 2L).paceDays()).isNull();
    }

    // ---- myLogs ----

    @Test
    void my_logs_defaults_to_challenge_period() {
        stubJoined(ntChallenge());
        when(logRepository.findByParticipationIdAndReadDateBetweenOrderByReadDateAsc(
                        any(), eq(LocalDate.of(2026, 6, 27)), eq(LocalDate.of(2026, 8, 25))))
                .thenReturn(List.of(ChallengeReadingLog.create(null, TODAY, 5)));

        assertThat(service.myLogs(1L, 2L, null, null)).hasSize(1);
    }
}
