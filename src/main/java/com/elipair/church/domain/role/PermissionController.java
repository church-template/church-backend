package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.PermissionResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 권한 카탈로그 조회(스펙 §5.4). /api/admin/** 인증 + ROLE_MANAGE. */
@RestController
@RequestMapping("/api/admin/permissions")
@PreAuthorize("hasAuthority('ROLE_MANAGE')")
public class PermissionController {

    private final PermissionService service;

    public PermissionController(PermissionService service) {
        this.service = service;
    }

    @GetMapping
    public List<PermissionResponse> list() {
        return service.list();
    }
}
