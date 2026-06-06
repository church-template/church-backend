package com.elipair.church.domain.sermon.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** 설교 부분 수정(PATCH) 요청. 전달된(비-null) 필드만 적용. tagIds null이면 태그 미변경. version 필수. */
public record SermonPatchRequest(
        @Size(max = 200) String title,
        @Size(max = 100) String preacher,
        @Size(max = 100) String series,
        @Size(max = 200) String scripture,
        @Size(max = 50000) String content,
        @Size(max = 500) String videoUrl,
        @Size(max = 500) String audioUrl,
        LocalDate preachedAt,
        List<Long> tagIds,
        @NotNull Long version) {}
