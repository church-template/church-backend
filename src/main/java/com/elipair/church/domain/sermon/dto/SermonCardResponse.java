package com.elipair.church.domain.sermon.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDate;
import java.util.List;

/** 설교 목록 카드(스펙 §5.5). content 제외 — 카드용 메타만. author = updated_by 표시 이름. */
public record SermonCardResponse(
        Long id,
        String title,
        String preacher,
        String series,
        String scripture,
        LocalDate preachedAt,
        long viewCount,
        List<TagResponse> tags,
        String author) {}
