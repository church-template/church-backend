package com.elipair.church.domain.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 부서 전체 수정(PUT). version 낙관락 비교용 필수. parentId=null이면 루트화, sortOrder=null이면 기존값 유지. */
public record DepartmentUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50000) String description,
        @Size(max = 100) String leader,
        Long parentId,
        @PositiveOrZero Integer sortOrder,
        @NotNull Long version) {}
