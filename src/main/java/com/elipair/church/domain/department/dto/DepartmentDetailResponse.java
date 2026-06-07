package com.elipair.church.domain.department.dto;

import java.time.LocalDateTime;

/** 부서 상세(스펙 §5.8). description·version 포함(version은 편집 재전송용 — 수정 응답은 flush로 post-increment). author 없음(설계 §1). */
public record DepartmentDetailResponse(
        Long id,
        String name,
        String description,
        String leader,
        Long parentId,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version) {}
