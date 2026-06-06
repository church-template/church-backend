package com.elipair.church.domain.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 공지 전체 수정(PUT) 요청. version은 낙관락 비교용 필수. isPinned null은 false로 간주(전체 교체). */
public record NoticeUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50000) String content,
        Boolean isPinned,
        List<Long> tagIds,
        @NotNull Long version) {}
