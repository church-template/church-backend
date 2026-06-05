package com.elipair.church.domain.tag;

import com.elipair.church.domain.tag.dto.TagCreateRequest;
import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.domain.tag.dto.TagUpdateRequest;
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
@RestController
public class AdminTagController {

    private final TagService service;

    public AdminTagController(TagService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/tags")
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/api/admin/tags/{id}")
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    public TagResponse update(@PathVariable Long id, @Valid @RequestBody TagUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/api/admin/tags/{id}")
    @PreAuthorize("hasAuthority('TAG_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
