package com.elipair.church.domain.role.dto;

import com.elipair.church.domain.role.Role;
import java.util.List;

public record RoleResponse(
        Long id,
        String name,
        int priority,
        boolean isSystem,
        String description,
        List<PermissionResponse> permissions) {

    public static RoleResponse from(Role role) {
        List<PermissionResponse> permissions =
                role.getPermissions().stream().map(PermissionResponse::from).toList();
        return new RoleResponse(
                role.getId(), role.getName(), role.getPriority(), role.isSystem(), role.getDescription(), permissions);
    }
}
