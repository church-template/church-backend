package com.elipair.church.domain.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RolePermissionsRequest(@NotNull List<@NotBlank @Size(max = 50) String> permissions) {}
