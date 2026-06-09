package com.elipair.church.domain.sermon;

import com.elipair.church.domain.sermon.dto.SermonCreateRequest;
import com.elipair.church.domain.sermon.dto.SermonDetailResponse;
import com.elipair.church.domain.sermon.dto.SermonPatchRequest;
import com.elipair.church.domain.sermon.dto.SermonUpdateRequest;
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

/** 설교 관리 API(스펙 §5.5). 전 메서드 SERMON_WRITE. */
@Tag(name = "설교")
@RestController
@PreAuthorize("hasAuthority('SERMON_WRITE')")
public class AdminSermonController {

    private final SermonService service;

    public AdminSermonController(SermonService service) {
        this.service = service;
    }

    @Operation(summary = "설교 등록", description = """
                    새 설교를 등록한다(201 Created).

                    - 인증(JWT): 필요 — `SERMON_WRITE`
                    - 요청 본문: `SermonCreateRequest` — 제목·설교자·시리즈·성경구절·본문·영상/오디오 URL·설교일(`title`·`preacher`·`preachedAt` 필수)·`tagIds`
                    - 반환값: `SermonDetailResponse` — 생성된 설교 상세
                    - 부수효과: `tagIds`로 태그 연결 · 메인 통합 API 캐시(`main`) 무효화
                    """)
    @PostMapping("/api/admin/sermons")
    public ResponseEntity<SermonDetailResponse> create(@Valid @RequestBody SermonCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "설교 전체 수정", description = """
                    설교 전체를 교체한다(PUT). 제공한 값으로 모든 필드를 덮어쓴다(미지정 선택 필드는 비워짐).

                    - 인증(JWT): 필요 — `SERMON_WRITE`
                    - 경로 변수: `id` — 수정할 설교 ID
                    - 요청 본문: `SermonUpdateRequest` — 제목·설교자·시리즈·성경구절·본문·영상/오디오 URL·설교일·`tagIds`·`version`(낙관락 비교용 필수)
                    - 반환값: `SermonDetailResponse` — 수정된 설교 상세(`version`은 증가 후 값)
                    - 부수효과: `version` 불일치 시 409 OPTIMISTIC_LOCK_CONFLICT · `tagIds`로 태그 재연결 · 메인 캐시(`main`) 무효화
                    """)
    @PutMapping("/api/admin/sermons/{id}")
    public SermonDetailResponse update(@PathVariable Long id, @Valid @RequestBody SermonUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "설교 부분 수정", description = """
                    설교를 부분 수정한다(PATCH). 전달된(비-null) 필드만 적용하고 `tagIds`가 null이면 태그는 미변경.

                    - 인증(JWT): 필요 — `SERMON_WRITE`
                    - 경로 변수: `id` — 수정할 설교 ID
                    - 요청 본문: `SermonPatchRequest` — 변경할 필드만(제목·설교자·시리즈·성경구절·본문·영상/오디오 URL·설교일·`tagIds`)·`version`(필수)
                    - 반환값: `SermonDetailResponse` — 수정된 설교 상세(`version`은 증가 후 값)
                    - 부수효과: `version` 불일치 시 409 OPTIMISTIC_LOCK_CONFLICT · `tagIds` 전달 시 태그 재연결 · 메인 캐시(`main`) 무효화
                    """)
    @PatchMapping("/api/admin/sermons/{id}")
    public SermonDetailResponse patch(@PathVariable Long id, @Valid @RequestBody SermonPatchRequest request) {
        return service.patch(id, request);
    }

    @Operation(summary = "설교 삭제", description = """
                    설교를 삭제한다(204 No Content).

                    - 인증(JWT): 필요 — `SERMON_WRITE`
                    - 경로 변수: `id` — 삭제할 설교 ID
                    - 반환값: 없음(204)
                    - 부수효과: soft delete(`deleted_at` 설정) · 연결된 태그 정리 · 메인 캐시(`main`) 무효화
                    """)
    @DeleteMapping("/api/admin/sermons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
