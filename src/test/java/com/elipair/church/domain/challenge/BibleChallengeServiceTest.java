package com.elipair.church.domain.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.challenge.dto.ChallengeCreateRequest;
import com.elipair.church.domain.challenge.dto.ChallengeDetailResponse;
import com.elipair.church.domain.challenge.dto.ChallengePatchRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BibleChallengeServiceTest {

    /** 고정 "오늘" = 2026-07-06 (KST). */
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-06T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 6);

    private BibleChallengeRepository repository;
    private ChallengeParticipationRepository participationRepository;
    private BibleChallengeService service;

    @BeforeEach
    void init() {
        repository = mock(BibleChallengeRepository.class);
        participationRepository = mock(ChallengeParticipationRepository.class);
        service = new BibleChallengeService(repository, participationRepository, FIXED);
    }

    private BibleChallenge ntChallenge() {
        // 신약 60일: 6/27 시작(오늘 10일차), 260장, 하루 목표 ceil(260/60)=5
        return BibleChallenge.create("학생부 신약 60일", "설명", 40, 66, LocalDate.of(2026, 6, 27), 60);
    }

    @Test
    void create_returns_derived_fields() {
        BibleChallenge saved = ntChallenge();
        when(repository.save(any(BibleChallenge.class))).thenReturn(saved);

        ChallengeDetailResponse res = service.create(
                new ChallengeCreateRequest("학생부 신약 60일", "설명", 40, 66, LocalDate.of(2026, 6, 27), 60));

        assertThat(res.totalChapters()).isEqualTo(260);
        assertThat(res.dailyGoal()).isEqualTo(5);
        assertThat(res.endDate()).isEqualTo(LocalDate.of(2026, 8, 25)); // 6/27 + 60일 - 1
        assertThat(res.status()).isEqualTo(ChallengeStatus.ONGOING);
        assertThat(res.joined()).isFalse();
    }

    @Test
    void create_with_inverted_book_range_throws_400() {
        assertThatThrownBy(() -> service.create(
                        new ChallengeCreateRequest("역순", null, 66, 40, TODAY, 100)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(repository, never()).save(any());
    }

    @Test
    void patch_with_stale_version_throws_409() {
        BibleChallenge c = ntChallenge();
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.patch(1L, new ChallengePatchRequest("새제목", null, null, null, null, null, 99L)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    void patch_structure_field_with_participants_throws_400() {
        BibleChallenge c = mock(BibleChallenge.class);
        when(c.getVersion()).thenReturn(0L);
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(c));
        when(participationRepository.existsByChallengeIdAndDeletedAtIsNull(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.patch(1L, new ChallengePatchRequest(null, null, null, null, null, 120, 0L)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(c, never()).applyPatch(any(), any(), any(), any(), any(), any());
    }

    @Test
    void patch_title_only_with_participants_is_allowed() {
        BibleChallenge c = ntChallenge();
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(c));
        when(participationRepository.existsByChallengeIdAndDeletedAtIsNull(1L)).thenReturn(true);

        ChallengeDetailResponse res = service.patch(1L, new ChallengePatchRequest("바뀐 제목", null, null, null, null, null, 0L));

        assertThat(res.title()).isEqualTo("바뀐 제목");
        verify(repository).flush();
    }

    @Test
    void patch_resulting_in_inverted_range_throws_400() {
        BibleChallenge c = ntChallenge(); // startBook=40
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(c));
        when(participationRepository.existsByChallengeIdAndDeletedAtIsNull(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.patch(1L, new ChallengePatchRequest(null, null, null, 39, null, null, 0L)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void get_unknown_throws_404() {
        when(repository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L, 2L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void get_marks_joined_for_participant() {
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(ntChallenge()));
        when(participationRepository.existsByChallengeIdAndMemberIdAndDeletedAtIsNull(1L, 2L)).thenReturn(true);

        assertThat(service.get(1L, 2L).joined()).isTrue();
    }

    @Test
    void delete_soft_deletes() {
        BibleChallenge c = mock(BibleChallenge.class);
        when(repository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(c));

        service.delete(1L);

        verify(c).softDelete();
    }
}
