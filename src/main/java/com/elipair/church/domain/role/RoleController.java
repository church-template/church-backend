package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.RoleCreateRequest;
import com.elipair.church.domain.role.dto.RolePermissionsRequest;
import com.elipair.church.domain.role.dto.RoleResponse;
import com.elipair.church.domain.role.dto.RoleUpdateRequest;
import com.elipair.church.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PostMapping
    public ResponseEntity<RoleResponse> create(
            @Valid @RequestBody RoleCreateRequest request, @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, principal.maxPriority()));
    }

    @PatchMapping("/{id}")
    public RoleResponse update(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return service.update(id, request, principal.maxPriority());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        service.delete(id, principal.maxPriority());
    }

    @PutMapping("/{id}/permissions")
    public RoleResponse setPermissions(
            @PathVariable Long id,
            @Valid @RequestBody RolePermissionsRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return service.setPermissions(id, request, principal.maxPriority());
    }
}
