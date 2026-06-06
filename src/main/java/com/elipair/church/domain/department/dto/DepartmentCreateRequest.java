package com.elipair.church.domain.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 부서 등록(POST). @Size(max)는 V10 컬럼 길이와 일치(description은 TEXT지만 스펙 §5 최소검증 상한).
 * parentId nullable=루트. sortOrder 미지정 시 서비스가 max+10 append. sortOrder는 음수 불가(@PositiveOrZero, positions 선례).
 */
public record DepartmentCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50000) String description,
        @Size(max = 100) String leader,
        Long parentId,
        @PositiveOrZero Integer sortOrder) {}
