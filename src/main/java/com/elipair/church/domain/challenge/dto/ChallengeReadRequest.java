package com.elipair.church.domain.challenge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/**
 * 읽음 기록 요청(설계 §3 read) — 둘 다 생략 가능.
 * chapters 기본 = 해당 날짜의 남은 목표치, date 기본 = 오늘(소급 = 챌린지 시작일~오늘).
 * 상한 1189(성경 전체 장 수) — 하루 기록이 이를 넘을 수 없고, int 오버플로(500)도 차단한다.
 */
public record ChallengeReadRequest(@Min(1) @Max(1189) Integer chapters, LocalDate date) {}
