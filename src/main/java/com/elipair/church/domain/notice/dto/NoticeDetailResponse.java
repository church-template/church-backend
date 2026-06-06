package com.elipair.church.domain.notice.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/** 공지 상세(스펙 §5.7). content·version 포함(version은 편집 재전송용 — update/patch는 서비스 flush로 post-increment 보장). author = updated_by 표시 이름. */
public record NoticeDetailResponse(
        Long id,
        String title,
        String content,
        boolean isPinned,
        long viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version,
        List<TagResponse> tags,
        String author) {}
