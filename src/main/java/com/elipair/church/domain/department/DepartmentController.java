package com.elipair.church.domain.department;

import com.elipair.church.domain.department.dto.DepartmentCardResponse;
import com.elipair.church.domain.department.dto.DepartmentDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 부서 공개 조회 API(스펙 §5.8). 비인증 — SecurityConfig anyRequest permitAll. 목록은 비페이징 평배열(positions/tags와 동일). */
@Tag(name = "부서", description = "부서 공개 조회/관리 API(스펙 §5.8). 계층 구조 지원; 하위 부서가 있는 경우 삭제 불가(409).")
@RestController
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @Operation(summary = "부서 목록", description = "공개. 카드 메타만(description 제외). 비페이징 평배열.")
    @GetMapping("/api/departments")
    public List<DepartmentCardResponse> list() {
        return service.list();
    }

    @Operation(summary = "부서 상세", description = "공개. description 포함. 계층 구조(부모·하위 부서) 정보 포함.")
    @GetMapping("/api/departments/{id}")
    public DepartmentDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
