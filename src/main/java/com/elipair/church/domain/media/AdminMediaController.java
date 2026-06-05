package com.elipair.church.domain.media;

import com.elipair.church.domain.media.dto.MediaReferencesResponse;
import com.elipair.church.domain.media.dto.MediaResponse;
import com.elipair.church.global.security.MemberPrincipal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 미디어 라이브러리 관리 API(스펙 §5.10). 전 메서드 MEDIA_MANAGE 필요. */
@RestController
@PreAuthorize("hasAuthority('MEDIA_MANAGE')")
public class AdminMediaController {

    private final MediaService service;

    public AdminMediaController(MediaService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/media")
    public ResponseEntity<MediaResponse> upload(
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(file, principal.id()));
    }

    @GetMapping("/api/admin/media")
    public Page<MediaResponse> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(type, from, to, pageable);
    }

    @GetMapping("/api/admin/media/{id}")
    public MediaResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/api/admin/media/{id}/references")
    public MediaReferencesResponse references(@PathVariable Long id) {
        return service.references(id);
    }

    @DeleteMapping("/api/admin/media/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
