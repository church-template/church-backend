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

    @Operation(summary = "직분 목록", description = """
            등록된 전체 직분(목사·장로 등)을 `sortOrder` 오름차순으로 조회한다(비페이징 평배열). 직분은 권한과 독립적이며 표시용 레이블로만 쓰인다.

            - 인증(JWT): 불필요 (공개)
            - 반환값: `List<PositionResponse>` — 각 직분의 id·name(한글)·`sortOrder`·`createdAt`
            """)
    @GetMapping("/api/positions")
    public List<PositionResponse> list() {
        return service.list();
    }

    @Operation(summary = "직분 추가", description = """
            새 직분을 추가한다. 직분 이름은 한글 사용자 표시용이며 권한과 무관하다.

            - 인증(JWT): 필요 — `POSITION_MANAGE`
            - 요청 본문: `PositionCreateRequest` — `name`(필수, ≤50)·`sortOrder`(선택, ≥0; 생략 시 기존 최대값+10 자동 부여)
            - 반환값: `PositionResponse` — 생성된 직분(201 Created)
            - 부수효과: `name` 중복 시 409 DUPLICATE_RESOURCE
            """)
    @PostMapping("/api/admin/positions")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    public ResponseEntity<PositionResponse> create(@Valid @RequestBody PositionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "직분 수정", description = """
            지정 직분의 name·sortOrder를 부분 수정한다(PATCH). null 필드는 미변경.

            - 인증(JWT): 필요 — `POSITION_MANAGE`
            - 경로 변수: `id` — 수정할 직분 ID
            - 요청 본문: `PositionUpdateRequest` — `name`(선택, ≤50)·`sortOrder`(선택, ≥0)
            - 반환값: `PositionResponse` — 수정된 직분
            - 부수효과: `name` 중복 시 409 DUPLICATE_RESOURCE
            """)
    @PatchMapping("/api/admin/positions/{id}")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    public PositionResponse update(@PathVariable Long id, @Valid @RequestBody PositionUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "직분 삭제", description = """
            지정 직분을 물리 삭제한다(soft delete 아님).

            - 인증(JWT): 필요 — `POSITION_MANAGE`
            - 경로 변수: `id` — 삭제할 직분 ID
            - 반환값: 없음(204)
            """)
    @DeleteMapping("/api/admin/positions/{id}")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
