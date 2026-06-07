package com.elipair.church.domain.gallery.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 앨범 부분 수정(PATCH). 전달된(비-null) 필드만 적용, tagIds null이면 태그 미변경. version 낙관락 필수. */
public record GalleryAlbumPatchRequest(
        @Size(max = 200) String title,
        @Size(max = 50000) String description,
        List<Long> tagIds,
        @NotNull Long version) {}
