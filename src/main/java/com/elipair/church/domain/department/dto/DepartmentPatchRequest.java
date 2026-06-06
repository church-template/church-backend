package com.elipair.church.domain.department.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 부서 부분 수정(PATCH). 전달된(비-null) 필드만 적용. parentId=null·sortOrder=null은 미변경(루트화는 PUT). version 필수.
 */
public record DepartmentPatchRequest(
        @Size(max = 100) String name,
        @Size(max = 50000) String description,
        @Size(max = 100) String leader,
        Long parentId,
        @PositiveOrZero Integer sortOrder,
        @NotNull Long version) {}
