package com.elipair.church.domain.sermon.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 설교 목록 카드(스펙 §5.5). content 제외 — 카드용 메타만. author = updated_by 표시 이름. */
public record SermonCardResponse(
        @Schema(description = "설교 ID") Long id,
        @Schema(description = "설교 제목", example = "부활의 증인") String title,
        @Schema(description = "설교자", example = "김목사") String preacher,
        @Schema(description = "시리즈명", example = "요한복음 시리즈") String series,

        @Schema(description = "본문 성경구절", example = "요한복음 20:19-23")
        String scripture,

        @Schema(description = "설교 날짜(yyyy-MM-dd)", example = "2026-06-01")
        LocalDate preachedAt,

        @Schema(description = "조회수") long viewCount,
        @Schema(description = "연결된 태그 목록") List<TagResponse> tags,

        @Schema(description = "표시 작성자(updated_by)", example = "김목사")
        String author) {}
