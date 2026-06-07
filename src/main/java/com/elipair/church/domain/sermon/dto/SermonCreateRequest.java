package com.elipair.church.domain.sermon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 설교 등록(POST) 요청. @Size(max)는 V7 컬럼 길이와 일치. content는 TEXT지만 스펙 §5 최소검증으로 상한 1개 부여. */
public record SermonCreateRequest(
        @Schema(description = "설교 제목", example = "부활의 증인") @NotBlank @Size(max = 200)
        String title,

        @Schema(description = "설교자", example = "김목사") @NotBlank @Size(max = 100)
        String preacher,

        @Schema(description = "시리즈명", example = "요한복음 시리즈") @Size(max = 100)
        String series,

        @Schema(description = "본문 성경구절", example = "요한복음 20:19-23") @Size(max = 200)
        String scripture,

        @Schema(description = "본문 마크다운. 이미지는 media:{id} 참조", example = "은혜로운 말씀 ![](media:42)") @Size(max = 50000)
        String content,

        @Schema(description = "동영상 URL", example = "https://www.youtube.com/watch?v=example") @Size(max = 500)
        String videoUrl,

        @Schema(description = "오디오 URL", example = "https://example.com/sermon.mp3") @Size(max = 500)
        String audioUrl,

        @Schema(description = "설교 날짜(yyyy-MM-dd)", example = "2026-06-01") @NotNull
        LocalDate preachedAt,

        @Schema(description = "태그 ID 목록") List<Long> tagIds) {}
