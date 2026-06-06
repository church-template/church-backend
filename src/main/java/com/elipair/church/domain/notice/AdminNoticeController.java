package com.elipair.church.domain.notice;

import com.elipair.church.domain.notice.dto.NoticeCreateRequest;
import com.elipair.church.domain.notice.dto.NoticeDetailResponse;
import com.elipair.church.domain.notice.dto.NoticePatchRequest;
import com.elipair.church.domain.notice.dto.NoticeUpdateRequest;
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
@RestController
@PreAuthorize("hasAuthority('NOTICE_WRITE')")
public class AdminNoticeController {

    private final NoticeService service;

    public AdminNoticeController(NoticeService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/notices")
    public ResponseEntity<NoticeDetailResponse> create(@Valid @RequestBody NoticeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/api/admin/notices/{id}")
    public NoticeDetailResponse update(@PathVariable Long id, @Valid @RequestBody NoticeUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/api/admin/notices/{id}")
    public NoticeDetailResponse patch(@PathVariable Long id, @Valid @RequestBody NoticePatchRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/api/admin/notices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
