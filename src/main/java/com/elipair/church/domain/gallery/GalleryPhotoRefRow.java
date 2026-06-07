package com.elipair.church.domain.gallery;

/** 사진 FK 미디어 참조 추적용 프로젝션 — 소속 앨범 (id, title)로 표면화. */
public interface GalleryPhotoRefRow {
    Long getId();

    String getTitle();
}
