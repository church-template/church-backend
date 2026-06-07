package com.elipair.church.domain.gallery.dto;

/** 앨범 상세에 임베드되는 사진 한 건(스펙 §5.12). caption은 MVP에서 null. */
public record GalleryPhotoResponse(Long id, Long mediaId, String caption, Integer sortOrder) {}
