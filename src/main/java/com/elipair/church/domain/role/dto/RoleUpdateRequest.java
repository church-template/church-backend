package com.elipair.church.domain.role.dto;

import jakarta.validation.constraints.Size;

public record RoleUpdateRequest(
        @Size(max = 50) String name,
        Integer priority,
        @Size(max = 255) String description) {}
