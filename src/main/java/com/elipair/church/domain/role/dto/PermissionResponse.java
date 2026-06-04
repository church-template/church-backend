package com.elipair.church.domain.role.dto;

import com.elipair.church.domain.role.Permission;

public record PermissionResponse(Long id, String name, String description) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getName(), permission.getDescription());
    }
}
