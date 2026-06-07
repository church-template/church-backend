package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.RoleCreateRequest;
import com.elipair.church.domain.role.dto.RolePermissionsRequest;
import com.elipair.church.domain.role.dto.RoleResponse;
import com.elipair.church.domain.role.dto.RoleUpdateRequest;
import com.elipair.church.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "역할", description = "역할 목록·생성·수정·삭제·권한 일괄설정 API(스펙 §5.4). 전 메서드 ROLE_MANAGE 필요.")
@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasAuthority('ROLE_MANAGE')")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @Operation(summary = "역할 목록", description = "ROLE_MANAGE 필요. 전체 역할 목록(우선순위·권한 포함)을 반환한다.")
    @GetMapping
    public List<RoleResponse> list() {
        return service.list();
    }

    @Operation(
            summary = "역할 생성",
            description =
                    "ROLE_MANAGE 필요. 요청자의 maxPriority 이하 priority만 설정 가능(위계 에스컬레이션 방지). is_system 역할은 생성 후 변경 불가.")
    @PostMapping
    public ResponseEntity<RoleResponse> create(
            @Valid @RequestBody RoleCreateRequest request, @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, principal.maxPriority()));
    }

    @Operation(
            summary = "역할 수정",
            description = "ROLE_MANAGE 필요. 대상 역할의 priority가 요청자 maxPriority보다 높으면 403. is_system 역할은 수정 불가.")
    @PatchMapping("/{id}")
    public RoleResponse update(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return service.update(id, request, principal.maxPriority());
    }

    @Operation(
            summary = "역할 삭제",
            description =
                    "ROLE_MANAGE 필요. 대상 역할의 priority가 요청자 maxPriority보다 높으면 403. is_system 역할 또는 마지막 SUPER_ADMIN 역할은 삭제 불가.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        service.delete(id, principal.maxPriority());
    }

    @Operation(
            summary = "역할 권한 일괄설정",
            description =
                    "ROLE_MANAGE 필요. 역할에 할당된 권한을 요청 목록으로 전체 교체한다. 대상 역할의 priority가 요청자 maxPriority보다 높거나 is_system이면 403.")
    @PutMapping("/{id}/permissions")
    public RoleResponse setPermissions(
            @PathVariable Long id,
            @Valid @RequestBody RolePermissionsRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return service.setPermissions(id, request, principal.maxPriority());
    }
}
