package com.elipair.church.domain.bulletin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 주보 상세(스펙 §5.13). mediaId로 PDF 접근(프론트가 /api/media/{id}). version은 편집 재전송용. */
public record BulletinDetailResponse(
        Long id,
        String title,
        LocalDate serviceDate,
        Long mediaId,
        String author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version) {}
