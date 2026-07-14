package com.elipair.church.domain.inquiry.dto;

import java.time.LocalDateTime;

/** 문의 상세. 답변 내용은 저장하지 않는다 — 답변은 이메일/문자로 직접 발송하고 완료 여부만 추적한다. */
public record InquiryDetailResponse(
        Long id,
        String name,
        String phone,
        String email,
        String content,
        boolean completed,
        LocalDateTime completedAt,
        LocalDateTime createdAt) {}
