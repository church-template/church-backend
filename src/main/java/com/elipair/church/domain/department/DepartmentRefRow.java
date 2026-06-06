package com.elipair.church.domain.department;

/** 미디어 참조 추적용 인터페이스 프로젝션 — (id, title) 한 행. title은 부서 name 별칭. */
public interface DepartmentRefRow {
    Long getId();

    String getTitle();
}
