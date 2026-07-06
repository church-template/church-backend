package com.elipair.church.domain.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 포인터 산술 — 회독 이월(설계 §3 read 4단계)·취소 역이월(설계 §3 취소). */
class ChallengeParticipationTest {

    @Test
    void advance_moves_pointer() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(12, 1189);
        assertThat(p.getChaptersRead()).isEqualTo(12);
        assertThat(p.getRoundsCompleted()).isZero();
    }

    @Test
    void advance_past_scope_completes_round_and_carries_over() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(1184, 1189);
        p.advance(12, 1189); // 5장 남았는데 12장 → 회독 +1, 새 포인터 7 (설계 §1 회독 이월)
        assertThat(p.getRoundsCompleted()).isEqualTo(1);
        assertThat(p.getChaptersRead()).isEqualTo(7);
    }

    @Test
    void advance_exactly_scope_resets_pointer_to_zero() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(10, 10);
        assertThat(p.getRoundsCompleted()).isEqualTo(1);
        assertThat(p.getChaptersRead()).isZero();
    }

    @Test
    void advance_can_complete_multiple_rounds_in_one_call() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(30, 10);
        assertThat(p.getRoundsCompleted()).isEqualTo(3);
        assertThat(p.getChaptersRead()).isZero();
    }

    @Test
    void rollback_reverses_across_round_boundary() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(1184, 1189);
        p.advance(12, 1189); // rounds=1, pointer=7
        p.rollback(12, 1189); // 원상복구 (설계 §3 취소: 회독 경계 역이월)
        assertThat(p.getRoundsCompleted()).isZero();
        assertThat(p.getChaptersRead()).isEqualTo(1184);
    }

    @Test
    void total_chapters_read_includes_completed_rounds() {
        ChallengeParticipation p = ChallengeParticipation.create(1L, 2L);
        p.advance(1189 + 7, 1189); // rounds=1, pointer=7
        assertThat(p.totalChaptersRead(1189)).isEqualTo(1196);
    }
}
