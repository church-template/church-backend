package com.elipair.church.domain.event.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/** 일정 목록 카드(스펙 §5.6). description·author·viewCount 제외 — 카드용 메타만(제목·장소·기간·종일·태그). */
public record EventCardResponse(
        Long id,
        String title,
        String location,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean allDay,
        List<TagResponse> tags) {}
