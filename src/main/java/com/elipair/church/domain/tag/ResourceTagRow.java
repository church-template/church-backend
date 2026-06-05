package com.elipair.church.domain.tag;

/** 목록 배치 조회용 인터페이스 프로젝션 — (resourceId, tagId, tagName) 한 행. */
public interface ResourceTagRow {
    Long getResourceId();

    Long getTagId();

    String getTagName();
}
