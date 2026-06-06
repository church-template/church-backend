package com.elipair.church.domain.event.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 일정 상세(스펙 §5.6). description·version 포함(version은 편집 재전송용 — 엔티티 필드 변경 시 flush로 post-increment,
 * tag-only 수정은 version 유지). author·viewCount 없음(설계 §1).
 */
public record EventDetailResponse(
        Long id,
        String title,
        String description,
        String location,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean allDay,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version,
        List<TagResponse> tags) {}
