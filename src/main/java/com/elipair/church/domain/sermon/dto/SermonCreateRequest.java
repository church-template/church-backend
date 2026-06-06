package com.elipair.church.domain.sermon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 설교 등록(POST) 요청. @Size(max)는 V7 컬럼 길이와 일치. content는 TEXT지만 스펙 §5 최소검증으로 상한 1개 부여. */
public record SermonCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 100) String preacher,
        @Size(max = 100) String series,
        @Size(max = 200) String scripture,
        @Size(max = 50000) String content,
        @Size(max = 500) String videoUrl,
        @Size(max = 500) String audioUrl,
        @NotNull LocalDate preachedAt,
        List<Long> tagIds) {}
