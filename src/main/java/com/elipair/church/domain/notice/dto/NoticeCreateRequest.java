package com.elipair.church.domain.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 공지 등록(POST) 요청. @Size(max)는 V8 컬럼 길이와 일치. content는 TEXT지만 스펙 §5 최소검증으로 상한 부여. isPinned 미지정 시 false. */
public record NoticeCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50000) String content,
        Boolean isPinned,
        List<Long> tagIds) {}
