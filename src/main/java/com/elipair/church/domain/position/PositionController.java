package com.elipair.church.domain.position;

import com.elipair.church.domain.position.dto.PositionCreateRequest;
import com.elipair.church.domain.position.dto.PositionResponse;
import com.elipair.church.domain.position.dto.PositionUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 직분 API(스펙 §5.3). GET은 공개(SecurityConfig anyRequest permitAll),
 * admin 3종은 /api/admin/** 인증 + POSITION_MANAGE 메서드 보안.
 */
@Tag(name = "직분", description = "직분(목사·장로 등) 목록 조회·추가·수정·삭제 API(스펙 §5.3). 조회는 공개, 변경은 POSITION_MANAGE 필요.")
@RestController
public class PositionController {

    private final PositionService service;

    public PositionController(PositionService service) {
        this.service = service;
    }

    @Operation(summary = "직분 목록", description = "공개. 시스템에 등록된 전체 직분 목록을 반환한다. 직분은 권한과 독립적이며 표시용 레이블로만 사용된다.")
    @GetMapping("/api/positions")
    public List<PositionResponse> list() {
        return service.list();
    }

    @Operation(summary = "직분 추가", description = "POSITION_MANAGE 필요. 새 직분을 추가한다. 직분 이름은 한글 사용자 표시용이며 권한과 무관하다.")
    @PostMapping("/api/admin/positions")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    public ResponseEntity<PositionResponse> create(@Valid @RequestBody PositionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "직분 수정", description = "POSITION_MANAGE 필요. 지정 직분의 이름을 수정한다.")
    @PatchMapping("/api/admin/positions/{id}")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    public PositionResponse update(@PathVariable Long id, @Valid @RequestBody PositionUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "직분 삭제", description = "POSITION_MANAGE 필요. 지정 직분을 삭제한다.")
    @DeleteMapping("/api/admin/positions/{id}")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
