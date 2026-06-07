package com.elipair.church.domain.department.dto;

/** 부서 목록 카드(스펙 §5.8). description(본문)·author 제외 — 메타만(이름·담당·상위·정렬). 프론트가 parentId로 트리 조립. */
public record DepartmentCardResponse(Long id, String name, String leader, Long parentId, Integer sortOrder) {}
