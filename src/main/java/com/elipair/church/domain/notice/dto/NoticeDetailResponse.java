package com.elipair.church.domain.notice.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 공지 상세(스펙 §5.7). content·version 포함(version은 편집 재전송용 — update/patch는 서비스 flush로 post-increment 보장). author = updated_by 표시 이름.
 * mediaUrls=본문 media:{id}별 서명 주소(같은 교회에 있는 것만, 없으면 빈 맵) — 교인 전용 메뉴에서도 파일이 열리게. */
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
        String author,
        Map<Long, String> mediaUrls) {}
