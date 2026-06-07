package com.elipair.church.domain.department;

import com.elipair.church.domain.department.dto.DepartmentCreateRequest;
import com.elipair.church.domain.department.dto.DepartmentDetailResponse;
import com.elipair.church.domain.department.dto.DepartmentPatchRequest;
import com.elipair.church.domain.department.dto.DepartmentUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 부서 관리 API(스펙 §5.8). 전 메서드 DEPT_WRITE. */
@Tag(name = "부서")
@RestController
@PreAuthorize("hasAuthority('DEPT_WRITE')")
public class AdminDepartmentController {

    private final DepartmentService service;

    public AdminDepartmentController(DepartmentService service) {
        this.service = service;
    }

    @Operation(summary = "부서 등록", description = "DEPT_WRITE 필요. parentId로 계층 구조 지정 가능.")
    @PostMapping("/api/admin/departments")
    public ResponseEntity<DepartmentDetailResponse> create(@Valid @RequestBody DepartmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "부서 전체 수정", description = "DEPT_WRITE. 낙관락(version) 필요, 충돌 시 409.")
    @PutMapping("/api/admin/departments/{id}")
    public DepartmentDetailResponse update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "부서 부분 수정", description = "DEPT_WRITE. 낙관락. null 필드는 미변경.")
    @PatchMapping("/api/admin/departments/{id}")
    public DepartmentDetailResponse patch(@PathVariable Long id, @Valid @RequestBody DepartmentPatchRequest request) {
        return service.patch(id, request);
    }

    @Operation(summary = "부서 삭제", description = "DEPT_WRITE. soft delete. 하위 부서가 존재하면 409로 삭제 차단.")
    @DeleteMapping("/api/admin/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
