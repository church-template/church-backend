package com.elipair.church.domain.notice;

import com.elipair.church.domain.notice.dto.NoticeCreateRequest;
import com.elipair.church.domain.notice.dto.NoticeDetailResponse;
import com.elipair.church.domain.notice.dto.NoticePatchRequest;
import com.elipair.church.domain.notice.dto.NoticeUpdateRequest;
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

/** 공지 관리 API(스펙 §5.7). 전 메서드 NOTICE_WRITE. */
@Tag(name = "공지")
@RestController
@PreAuthorize("hasAuthority('NOTICE_WRITE')")
public class AdminNoticeController {

    private final NoticeService service;

    public AdminNoticeController(NoticeService service) {
        this.service = service;
    }

    @Operation(summary = "공지 등록", description = """
                    새 공지를 등록한다(201 Created).

                    - 인증(JWT): 필요 — `NOTICE_WRITE`
                    - 요청 본문: `NoticeCreateRequest` — 제목(`title` 필수)·본문·`isPinned`(미지정 시 false)·`tagIds`
                    - 반환값: `NoticeDetailResponse` — 생성된 공지 상세
                    - 부수효과: `tagIds`로 태그 연결 · 메인 통합 API 캐시(`main`) 무효화
                    """)
    @PostMapping("/api/admin/notices")
    public ResponseEntity<NoticeDetailResponse> create(@Valid @RequestBody NoticeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "공지 전체 수정", description = """
                    공지 전체를 교체한다(PUT). 제공한 값으로 모든 필드를 덮어쓴다(`isPinned` 미지정 시 false로 교체).

                    - 인증(JWT): 필요 — `NOTICE_WRITE`
                    - 경로 변수: `id` — 수정할 공지 ID
                    - 요청 본문: `NoticeUpdateRequest` — 제목·본문·`isPinned`·`tagIds`·`version`(낙관락 비교용 필수)
                    - 반환값: `NoticeDetailResponse` — 수정된 공지 상세(`version`은 증가 후 값)
                    - 부수효과: `version` 불일치 시 409 OPTIMISTIC_LOCK_CONFLICT · `tagIds`로 태그 재연결 · 메인 캐시(`main`) 무효화
                    """)
    @PutMapping("/api/admin/notices/{id}")
    public NoticeDetailResponse update(@PathVariable Long id, @Valid @RequestBody NoticeUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "공지 부분 수정", description = """
                    공지를 부분 수정한다(PATCH). 전달된(비-null) 필드만 적용하고 `tagIds`가 null이면 태그는 미변경.

                    - 인증(JWT): 필요 — `NOTICE_WRITE`
                    - 경로 변수: `id` — 수정할 공지 ID
                    - 요청 본문: `NoticePatchRequest` — 변경할 필드만(제목·본문·`isPinned` 토글·`tagIds`)·`version`(필수)
                    - 반환값: `NoticeDetailResponse` — 수정된 공지 상세(`version`은 증가 후 값)
                    - 부수효과: `version` 불일치 시 409 OPTIMISTIC_LOCK_CONFLICT · `tagIds` 전달 시 태그 재연결 · 메인 캐시(`main`) 무효화
                    """)
    @PatchMapping("/api/admin/notices/{id}")
    public NoticeDetailResponse patch(@PathVariable Long id, @Valid @RequestBody NoticePatchRequest request) {
        return service.patch(id, request);
    }

    @Operation(summary = "공지 삭제", description = """
                    공지를 삭제한다(204 No Content).

                    - 인증(JWT): 필요 — `NOTICE_WRITE`
                    - 경로 변수: `id` — 삭제할 공지 ID
                    - 반환값: 없음(204)
                    - 부수효과: soft delete(`deleted_at` 설정) · 연결된 태그 정리 · 메인 캐시(`main`) 무효화
                    """)
    @DeleteMapping("/api/admin/notices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
