package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.RoleResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 역할·권한 관리 API(스펙 §5.4). /api/admin/** 인증 + ROLE_MANAGE(클래스 레벨). */
@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasAuthority('ROLE_MANAGE')")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return service.list();
    }
}
