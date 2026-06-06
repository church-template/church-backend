package com.elipair.church.domain.notice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 공지 부분 수정(PATCH) 요청. 전달된(비-null) 필드만 적용(isPinned 토글 포함). tagIds null이면 태그 미변경. version 필수. */
public record NoticePatchRequest(
        @Size(max = 200) String title,
        @Size(max = 50000) String content,
        Boolean isPinned,
        List<Long> tagIds,
        @NotNull Long version) {}
