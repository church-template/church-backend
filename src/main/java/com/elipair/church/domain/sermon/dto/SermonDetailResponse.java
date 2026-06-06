package com.elipair.church.domain.sermon.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 설교 상세(스펙 §5.5). content·version 포함(version은 편집 재전송용). author = updated_by 표시 이름. */
public record SermonDetailResponse(
        Long id,
        String title,
        String preacher,
        String series,
        String scripture,
        String content,
        String videoUrl,
        String audioUrl,
        LocalDate preachedAt,
        long viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version,
        List<TagResponse> tags,
        String author) {}
