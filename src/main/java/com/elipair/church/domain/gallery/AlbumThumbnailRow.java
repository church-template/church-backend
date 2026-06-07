package com.elipair.church.domain.gallery;

/** 앨범 목록 썸네일 배치 조회용 프로젝션 — (albumId, mediaId). 첫 사진(min sort_order). */
public interface AlbumThumbnailRow {
    Long getAlbumId();

    Long getMediaId();
}
