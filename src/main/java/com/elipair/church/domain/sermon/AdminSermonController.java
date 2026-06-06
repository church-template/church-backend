package com.elipair.church.domain.sermon;

import com.elipair.church.domain.sermon.dto.SermonCreateRequest;
import com.elipair.church.domain.sermon.dto.SermonDetailResponse;
import com.elipair.church.domain.sermon.dto.SermonPatchRequest;
import com.elipair.church.domain.sermon.dto.SermonUpdateRequest;
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
@RestController
@PreAuthorize("hasAuthority('SERMON_WRITE')")
public class AdminSermonController {

    private final SermonService service;

    public AdminSermonController(SermonService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/sermons")
    public ResponseEntity<SermonDetailResponse> create(@Valid @RequestBody SermonCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/api/admin/sermons/{id}")
    public SermonDetailResponse update(@PathVariable Long id, @Valid @RequestBody SermonUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/api/admin/sermons/{id}")
    public SermonDetailResponse patch(@PathVariable Long id, @Valid @RequestBody SermonPatchRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/api/admin/sermons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
