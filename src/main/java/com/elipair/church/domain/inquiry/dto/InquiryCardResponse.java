package com.elipair.church.domain.inquiry.dto;

import java.time.LocalDateTime;

/** 문의 목록 카드. 본문 content는 상세에서만 제공(스펙 §5 리스트 카드 관례). */
public record InquiryCardResponse(
        Long id,
        String name,
        String phone,
        String email,
        boolean completed,
        LocalDateTime completedAt,
        LocalDateTime createdAt) {}
