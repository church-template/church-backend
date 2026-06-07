package com.elipair.church.domain.bulletin;

/** 주보 FK 미디어 참조 추적용 프로젝션 (id, title). */
public interface BulletinRefRow {
    Long getId();

    String getTitle();
}
