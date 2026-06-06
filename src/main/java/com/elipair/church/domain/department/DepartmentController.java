package com.elipair.church.domain.department;

import com.elipair.church.domain.department.dto.DepartmentCardResponse;
import com.elipair.church.domain.department.dto.DepartmentDetailResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 부서 공개 조회 API(스펙 §5.8). 비인증 — SecurityConfig anyRequest permitAll. 목록은 비페이징 평배열(positions/tags와 동일). */
@RestController
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping("/api/departments")
    public List<DepartmentCardResponse> list() {
        return service.list();
    }

    @GetMapping("/api/departments/{id}")
    public DepartmentDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
