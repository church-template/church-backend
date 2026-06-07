package com.elipair.church.domain.department;

import com.elipair.church.domain.department.dto.DepartmentCreateRequest;
import com.elipair.church.domain.department.dto.DepartmentDetailResponse;
import com.elipair.church.domain.department.dto.DepartmentPatchRequest;
import com.elipair.church.domain.department.dto.DepartmentUpdateRequest;
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
@RestController
@PreAuthorize("hasAuthority('DEPT_WRITE')")
public class AdminDepartmentController {

    private final DepartmentService service;

    public AdminDepartmentController(DepartmentService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/departments")
    public ResponseEntity<DepartmentDetailResponse> create(@Valid @RequestBody DepartmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/api/admin/departments/{id}")
    public DepartmentDetailResponse update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/api/admin/departments/{id}")
    public DepartmentDetailResponse patch(@PathVariable Long id, @Valid @RequestBody DepartmentPatchRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/api/admin/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
