package com.elipair.church.domain.member.dto;

import jakarta.validation.constraints.NotNull;

public record RoleGrantRequest(@NotNull Long roleId) {}
