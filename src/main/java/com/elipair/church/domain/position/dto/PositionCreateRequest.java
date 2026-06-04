package com.elipair.church.domain.position.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PositionCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @PositiveOrZero Integer sortOrder) {}
