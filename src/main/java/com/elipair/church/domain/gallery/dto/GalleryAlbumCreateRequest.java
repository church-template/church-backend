package com.elipair.church.domain.gallery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 앨범 생성(POST). @Size(max)는 V11 컬럼 길이/스펙 §5 최소검증 상한. */
public record GalleryAlbumCreateRequest(
        @NotBlank @Size(max = 200) String title, @Size(max = 50000) String description, List<Long> tagIds) {}
