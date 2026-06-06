package com.elipair.church.domain.sermon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 설교 전체 수정(PUT) 요청. version은 낙관락 비교용 필수. null 필드는 해당 컬럼을 비운다. */
public record SermonUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 100) String preacher,
        @Size(max = 100) String series,
        @Size(max = 200) String scripture,
        @Size(max = 50000) String content,
        @Size(max = 500) String videoUrl,
        @Size(max = 500) String audioUrl,
        @NotNull LocalDate preachedAt,
        List<Long> tagIds,
        @NotNull Long version) {}
