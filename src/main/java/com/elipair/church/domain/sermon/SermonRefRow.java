package com.elipair.church.domain.sermon;

/** 미디어 참조 추적용 인터페이스 프로젝션 — (id, title) 한 행. */
public interface SermonRefRow {
    Long getId();

    String getTitle();
}
