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

    @Operation(summary = "부서 등록", description = """
                    새 부서를 생성한다.

                    - 인증(JWT): 필요 — `DEPT_WRITE`
                    - 요청 본문: `DepartmentCreateRequest` — 이름·description·담당(leader)·`parentId`(null=루트)·`sortOrder`(미지정 시 max+10 append)
                    - 반환값: `DepartmentDetailResponse` — 생성된 부서 상세(201)
                    - 부수효과: `parentId` 지정 시 상위 부서 존재·미삭제 검증(미존재 400 INVALID_INPUT_VALUE)
                    """)
    @PostMapping("/api/admin/departments")
    public ResponseEntity<DepartmentDetailResponse> create(@Valid @RequestBody DepartmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "부서 전체 수정", description = """
                    부서 전체 교체(PUT). 제공한 값으로 모든 필드를 덮어쓴다.

                    - 인증(JWT): 필요 — `DEPT_WRITE`
                    - 경로 변수: `id` — 수정할 부서 ID
                    - 요청 본문: `DepartmentUpdateRequest` — 이름·description·담당·`parentId`(null=루트화)·`sortOrder`(null=기존 유지)·`version`(필수)
                    - 반환값: `DepartmentDetailResponse` — 수정된 부서 상세
                    - 부수효과: `version` 불일치 시 409 OPTIMISTIC_LOCK_CONFLICT · 상위 부서 검증(자기참조·순환·미존재 400 INVALID_INPUT_VALUE)
                    """)
    @PutMapping("/api/admin/departments/{id}")
    public DepartmentDetailResponse update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "부서 부분 수정", description = """
                    부서 부분 수정(PATCH). 전달된(비-null) 필드만 적용한다.

                    - 인증(JWT): 필요 — `DEPT_WRITE`
                    - 경로 변수: `id` — 수정할 부서 ID
                    - 요청 본문: `DepartmentPatchRequest` — 이름·description·담당·`parentId`(null=미변경; 루트화는 PUT)·`sortOrder`(null=미변경)·`version`(필수)
                    - 반환값: `DepartmentDetailResponse` — 수정된 부서 상세
                    - 부수효과: `version` 불일치 시 409 OPTIMISTIC_LOCK_CONFLICT · `parentId` 변경 시 상위 부서 검증(자기참조·순환·미존재 400 INVALID_INPUT_VALUE)
                    """)
    @PatchMapping("/api/admin/departments/{id}")
    public DepartmentDetailResponse patch(@PathVariable Long id, @Valid @RequestBody DepartmentPatchRequest request) {
        return service.patch(id, request);
    }

    @Operation(summary = "부서 삭제", description = """
                    부서를 삭제한다.

                    - 인증(JWT): 필요 — `DEPT_WRITE`
                    - 경로 변수: `id` — 삭제할 부서 ID
                    - 반환값: 없음(204)
                    - 부수효과: soft delete(deleted_at) · 하위 부서 존재 시 409 DEPARTMENT_HAS_CHILDREN로 삭제 차단
                    """)
    @DeleteMapping("/api/admin/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
