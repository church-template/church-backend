package com.elipair.church.domain.gallery.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 앨범 목록 카드(스펙 §5.12) — 본문 description 제외. thumbnailMediaId=첫 사진 media_id(없으면 null),
 * photoCount=앨범 내 사진 수. author=updated_by 표시명(탈퇴 마스킹).
 */
public record GalleryAlbumCardResponse(
        Long id,
        String title,
        Long thumbnailMediaId,
        long photoCount,
        LocalDateTime createdAt,
        List<TagResponse> tags,
        String author) {}
