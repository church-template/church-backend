package com.elipair.church.domain.gallery;

/** 앨범 목록 사진수 배치 조회용 프로젝션 — (albumId, count). */
public interface AlbumPhotoCountRow {
    Long getAlbumId();

    Long getCount();
}
