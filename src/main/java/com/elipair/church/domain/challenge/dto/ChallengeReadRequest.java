package com.elipair.church.domain.challenge.dto;

import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/**
 * 읽음 기록 요청(설계 §3 read) — 둘 다 생략 가능.
 * chapters 기본 = 해당 날짜의 남은 목표치, date 기본 = 오늘(소급 = 챌린지 시작일~오늘).
 */
public record ChallengeReadRequest(@Min(1) Integer chapters, LocalDate date) {}
