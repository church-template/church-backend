package com.elipair.church.domain.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoleCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull Integer priority,
        @Size(max = 255) String description) {}
