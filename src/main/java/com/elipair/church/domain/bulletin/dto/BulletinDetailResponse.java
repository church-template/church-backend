package com.elipair.church.domain.bulletin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 주보 상세(스펙 §5.13). url = PDF 서명 주소(주보가 교인 전용이어도 열린다, PDF 없으면 null). version은 편집 재전송용. */
public record BulletinDetailResponse(
        Long id,
        String title,
        LocalDate serviceDate,
        Long mediaId,
        String author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version,
        String url) {}
