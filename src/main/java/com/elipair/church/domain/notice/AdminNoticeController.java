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

    @Operation(summary = "공지 등록", description = "NOTICE_WRITE 필요. tagIds로 태그 연결.")
    @PostMapping("/api/admin/notices")
    public ResponseEntity<NoticeDetailResponse> create(@Valid @RequestBody NoticeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "공지 전체 수정", description = "NOTICE_WRITE. 낙관락(version) 필요, 충돌 시 409.")
    @PutMapping("/api/admin/notices/{id}")
    public NoticeDetailResponse update(@PathVariable Long id, @Valid @RequestBody NoticeUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "공지 부분 수정", description = "NOTICE_WRITE. 낙관락. null 필드는 미변경.")
    @PatchMapping("/api/admin/notices/{id}")
    public NoticeDetailResponse patch(@PathVariable Long id, @Valid @RequestBody NoticePatchRequest request) {
        return service.patch(id, request);
    }

    @Operation(summary = "공지 삭제", description = "NOTICE_WRITE. soft delete.")
    @DeleteMapping("/api/admin/notices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
