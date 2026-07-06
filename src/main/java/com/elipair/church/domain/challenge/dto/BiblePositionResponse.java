package com.elipair.church.domain.challenge.dto;

/** 현재 읽은 위치 — 한글 권 이름 + 장(설계 §3 currentPosition). */
public record BiblePositionResponse(String book, int chapter) {}
