package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.PermissionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 권한 카탈로그 조회(스펙 §5.4). /api/admin/** 인증 + ROLE_MANAGE. */
@Tag(name = "권한", description = "시스템에 정의된 권한 카탈로그 조회 API(스펙 §5.4). ROLE_MANAGE 필요.")
@RestController
@RequestMapping("/api/admin/permissions")
@PreAuthorize("hasAuthority('ROLE_MANAGE')")
public class PermissionController {

    private final PermissionService service;

    public PermissionController(PermissionService service) {
        this.service = service;
    }

    @Operation(
            summary = "권한 목록",
            description = "ROLE_MANAGE 필요. 시스템에 정의된 전체 권한 카탈로그를 반환한다. 역할 권한 일괄설정 시 유효한 permissionId 목록 확인에 사용한다.")
    @GetMapping
    public List<PermissionResponse> list() {
        return service.list();
    }
}
