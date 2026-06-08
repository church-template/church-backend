package com.elipair.church.domain.tag;

import com.elipair.church.domain.tag.dto.TagCreateRequest;
import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.domain.tag.dto.TagUpdateRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 태그 관리 API(스펙 §5.11). /api/admin/** 인증 + TAG_MANAGE 메서드 보안. */
@Tag(name = "태그")
@RestController
public class AdminTagController {

    private final TagService service;

    public AdminTagController(TagService service) {
        this.service = service;
    }

    @Operation(summary = "태그 추가", description = """
            설교·공지·일정·부서에 다형 연결 가능한 태그를 생성한다(전역 태그 풀).

            - 인증(JWT): 필요 — `TAG_MANAGE`
            - 요청 본문: `TagCreateRequest` — `name`(필수, ≤50)
            - 반환값: `TagResponse` — 생성된 태그(201 Created)
            - 부수효과: `name` 중복 시 409 DUPLICATE_RESOURCE
            """)
    @PostMapping("/api/admin/tags")
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "태그 수정", description = """
            지정 태그의 이름을 변경한다(PATCH). `name` null이면 미변경.

            - 인증(JWT): 필요 — `TAG_MANAGE`
            - 경로 변수: `id` — 수정할 태그 ID
            - 요청 본문: `TagUpdateRequest` — `name`(선택, ≤50)
            - 반환값: `TagResponse` — 수정된 태그
            - 부수효과: `name` 중복 시 409 DUPLICATE_RESOURCE
            """)
    @PatchMapping("/api/admin/tags/{id}")
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    public TagResponse update(@PathVariable Long id, @Valid @RequestBody TagUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "태그 삭제", description = """
            지정 태그를 물리 삭제한다. 미디어와 달리 비차단 삭제.

            - 인증(JWT): 필요 — `TAG_MANAGE`
            - 경로 변수: `id` — 삭제할 태그 ID
            - 반환값: 없음(204)
            - 부수효과: 해당 태그의 모든 `content_tags` 연결을 먼저 정리한 뒤 태그를 삭제(비차단 — 사용 중이어도 차단하지 않음)
            """)
    @DeleteMapping("/api/admin/tags/{id}")
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
