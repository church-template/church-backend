package com.elipair.church.domain.event.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 일정 상세(스펙 §5.6). description·version 포함(version은 편집 재전송용 — 엔티티 필드 변경 시 flush로 post-increment,
 * tag-only 수정은 version 유지). author·viewCount 없음(설계 §1).
 * mediaUrls=설명(description) media:{id}별 서명 주소(같은 교회에 있는 것만, 없으면 빈 맵) — 교인 전용 메뉴에서도 파일이 열리게.
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
        List<TagResponse> tags,
        Map<Long, String> mediaUrls) {}
