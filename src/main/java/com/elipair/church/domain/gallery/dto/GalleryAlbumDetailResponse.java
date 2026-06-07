package com.elipair.church.domain.gallery.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/** 앨범 상세(스펙 §5.12) — description·사진 목록·version(편집 재전송용)·작성자 포함. */
public record GalleryAlbumDetailResponse(
        Long id,
        String title,
        String description,
        List<TagResponse> tags,
        String author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version,
        List<GalleryPhotoResponse> photos) {}
