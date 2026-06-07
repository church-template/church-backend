package com.elipair.church.domain.bulletin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 주보 목록 카드(스펙 §5.13). 본문 없음. author = updated_by 표시 이름. */
public record BulletinCardResponse(
        Long id, String title, LocalDate serviceDate, Long mediaId, LocalDateTime createdAt, String author) {}
