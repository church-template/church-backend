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

    @Operation(summary = "업로드", description = """
                    이미지·PDF를 미디어 라이브러리에 업로드한다. 생성된 `media.id`를 본문에 `media:{id}` 형태로 참조해 재사용한다.

                    - 인증(JWT): 필요 — `MEDIA_MANAGE`
                    - 요청 본문/업로드: `file`(multipart/form-data) — 업로드 파일. 매직바이트로 형식 확정(JPEG/PNG/GIF/WEBP/PDF 5종만 허용)
                    - 반환값: `MediaResponse` — 생성된 미디어(id·파일명·mimeType·size·업로더·생성일), 201 Created
                    - 부수효과: 한도(기본 10MB) 초과 시 413 FILE_SIZE_EXCEEDED · 미허용 형식은 파일을 쓰지 않고 거부
                    """)
    @PostMapping("/api/admin/media")
    public ResponseEntity<MediaResponse> upload(
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(file, principal.id()));
    }

    @Operation(summary = "목록", description = """
                    미디어 라이브러리 목록을 조회한다.

                    - 인증(JWT): 필요 — `MEDIA_MANAGE`
                    - 요청 파라미터: `type` — `image`|`pdf` 필터(생략 시 전체); `from`·`to` — 업로드일 범위(`yyyy-MM-dd`, 상한 포함); `page`·`size`·`sort`(기본 `createdAt,desc`)
                    - 반환값: `Page<MediaResponse>` — 페이지네이션 목록(미디어 메타)
                    """)
    @GetMapping("/api/admin/media")
    public Page<MediaResponse> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(type, from, to, pageable);
    }

    @Operation(summary = "단건 조회", description = """
                    미디어 한 건의 메타를 조회한다(파일 바이트가 아닌 메타).

                    - 인증(JWT): 필요 — `MEDIA_MANAGE`
                    - 경로 변수: `id` — 조회할 미디어 ID
                    - 반환값: `MediaResponse` — 미디어 메타(id·파일명·mimeType·size·업로더·생성일)
                    """)
    @GetMapping("/api/admin/media/{id}")
    public MediaResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "참조목록", description = """
                    이 미디어를 참조하는 콘텐츠 목록을 조회한다(삭제 전 확인용).

                    - 인증(JWT): 필요 — `MEDIA_MANAGE`
                    - 경로 변수: `id` — 대상 미디어 ID
                    - 반환값: `MediaReferencesResponse` — `inUse` 여부 + `references`(`ContentRef`: type·id·title 목록)
                    - 부수효과: 참조 추적은 본문 `LIKE '%media:{id}%'` 와 `gallery_photos`·`bulletins`의 `media_id` FK 합집합(별도 조인 테이블 없음)
                    """)
    @GetMapping("/api/admin/media/{id}/references")
    public MediaReferencesResponse references(@PathVariable Long id) {
        return service.references(id);
    }

    @Operation(summary = "삭제", description = """
                    미디어를 차단형으로 삭제한다(라이브러리 원본 제거). 파일과 DB 레코드를 모두 지우는 유일한 경로다.

                    - 인증(JWT): 필요 — `MEDIA_MANAGE`
                    - 경로 변수: `id` — 삭제할 미디어 ID
                    - 반환값: 없음(204)
                    - 부수효과: 차단형 삭제 — 본문·`gallery_photos`·`bulletins`에 참조가 1건이라도 있으면 409 MEDIA_IN_USE + `references` 목록 반환 · 참조 0일 때만 DB 행을 먼저 확정(flush) 후 파일 제거(I/O 실패 시 롤백으로 깨진 참조 0)
                    """)
    @DeleteMapping("/api/admin/media/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
