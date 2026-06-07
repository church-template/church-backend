package com.elipair.church.domain.media;

import com.elipair.church.domain.media.dto.MediaReferencesResponse;
import com.elipair.church.domain.media.dto.MediaResponse;
import com.elipair.church.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "미디어(관리)", description = "미디어 라이브러리 관리 API(스펙 §5.10). 전 메서드 MEDIA_MANAGE 필요.")
@RestController
@PreAuthorize("hasAuthority('MEDIA_MANAGE')")
public class AdminMediaController {

    private final MediaService service;

    public AdminMediaController(MediaService service) {
        this.service = service;
    }

    @Operation(
            summary = "업로드",
            description = "MEDIA_MANAGE. multipart/form-data로 이미지·PDF 업로드. 생성된 media.id를 본문에 media:{id} 형태로 참조한다.")
    @PostMapping("/api/admin/media")
    public ResponseEntity<MediaResponse> upload(
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(file, principal.id()));
    }

    @Operation(summary = "목록", description = "MEDIA_MANAGE. 미디어 라이브러리 목록. type(image/pdf)·업로드일 범위 필터, 페이지네이션.")
    @GetMapping("/api/admin/media")
    public Page<MediaResponse> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(type, from, to, pageable);
    }

    @Operation(summary = "단건 조회", description = "MEDIA_MANAGE. 미디어 단건 상세 조회.")
    @GetMapping("/api/admin/media/{id}")
    public MediaResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(
            summary = "참조목록",
            description =
                    "MEDIA_MANAGE. 해당 미디어를 참조하는 콘텐츠 목록(본문 LIKE 'media:{id}' + gallery_photos/bulletins FK). 삭제 전 확인용.")
    @GetMapping("/api/admin/media/{id}/references")
    public MediaReferencesResponse references(@PathVariable Long id) {
        return service.references(id);
    }

    @Operation(
            summary = "삭제",
            description =
                    "MEDIA_MANAGE. 차단형 삭제. 본문·gallery_photos·bulletins에 참조가 1건이라도 있으면 409 MEDIA_IN_USE + 참조목록 반환. 참조가 없을 때만 파일 + DB 레코드를 제거한다.")
    @DeleteMapping("/api/admin/media/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
