package com.elipair.church.domain.notice.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/** 공지 목록 카드(스펙 §5.7). content 제외 — 카드용 메타만(제목·고정·조회수·작성일·태그·작성자). author = updated_by 표시 이름. */
public record NoticeCardResponse(
        Long id,
        String title,
        boolean isPinned,
        long viewCount,
        LocalDateTime createdAt,
        List<TagResponse> tags,
        String author) {}
