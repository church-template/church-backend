package com.elipair.church.domain.challenge.dto;

import java.time.LocalDate;

/** 달력 히트맵용 날짜별 로그(설계 §3 my-logs). */
public record ReadingLogResponse(LocalDate readDate, int chapters) {}
