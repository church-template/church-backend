package com.elipair.church.domain.bulletin;

import com.elipair.church.domain.bulletin.dto.BulletinDetailResponse;
import com.elipair.church.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주보 관리 API(스펙 §5.13). 전 메서드 BULLETIN_WRITE. multipart(file XOR mediaId).
 * 스칼라는 required=false로 받고 필수성·XOR·공백은 서비스에서 검증(업로드 전, 설계 §6.1).
 */
@Tag(name = "주보", description = "주보 공개 조회/관리 API(스펙 §5.13). PDF media 재사용 기반.")
@RestController
@PreAuthorize("hasAuthority('BULLETIN_WRITE')")
public class AdminBulletinController {

    private final BulletinService service;

    public AdminBulletinController(BulletinService service) {
        this.service = service;
    }

    @Operation(
            summary = "주보 업로드",
            description =
                    "BULLETIN_WRITE. multipart: file(신규 PDF 업로드) XOR mediaId(기존 media 재사용) 중 하나만 허용. title·serviceDate 필수. PDF는 media 라이브러리에 저장 후 bulletins.media_id로 참조.")
    @PostMapping("/api/admin/bulletins")
    public ResponseEntity<BulletinDetailResponse> create(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) Long mediaId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(title, serviceDate, file, mediaId, principal.id()));
    }

    @Operation(
            summary = "주보 수정",
            description =
                    "BULLETIN_WRITE. 부분 수정. version 필수(낙관락, 충돌 시 409). file XOR mediaId로 PDF 교체 가능. null 파라미터는 미변경.")
    @PatchMapping("/api/admin/bulletins/{id}")
    public BulletinDetailResponse patch(
            @PathVariable Long id,
            @RequestParam(required = false) Long version,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) Long mediaId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return service.patch(id, version, title, serviceDate, file, mediaId, principal.id());
    }

    @Operation(
            summary = "주보 삭제",
            description = "BULLETIN_WRITE. soft delete. media 원본(PDF 파일)은 보존되므로 실제 파일 삭제는 미디어 삭제 API를 별도 사용.")
    @DeleteMapping("/api/admin/bulletins/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
