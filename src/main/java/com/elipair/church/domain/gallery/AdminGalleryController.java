package com.elipair.church.domain.gallery;

import com.elipair.church.domain.gallery.dto.GalleryAlbumCreateRequest;
import com.elipair.church.domain.gallery.dto.GalleryAlbumDetailResponse;
import com.elipair.church.domain.gallery.dto.GalleryAlbumPatchRequest;
import com.elipair.church.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 갤러리 관리 API(스펙 §5.12). 전 메서드 GALLERY_WRITE. 사진 추가는 multipart(files/mediaIds 혼합). */
@Tag(name = "갤러리(관리)", description = "갤러리 앨범·사진 관리 API(스펙 §5.12). 전 메서드 GALLERY_WRITE 필요.")
@RestController
@PreAuthorize("hasAuthority('GALLERY_WRITE')")
public class AdminGalleryController {

    private final GalleryAlbumService albumService;
    private final GalleryPhotoService photoService;

    public AdminGalleryController(GalleryAlbumService albumService, GalleryPhotoService photoService) {
        this.albumService = albumService;
        this.photoService = photoService;
    }

    @Operation(summary = "앨범 생성", description = """
                    앨범 메타를 생성한다. 사진은 별도 사진 추가 엔드포인트로 넣는다.

                    - 인증(JWT): 필요 — `GALLERY_WRITE`
                    - 요청 본문: `GalleryAlbumCreateRequest` — `title`(필수)·`description`·`tagIds`
                    - 반환값: `GalleryAlbumDetailResponse` — 생성된 앨범 상세(사진 목록 빈 상태), 201 Created
                    - 부수효과: `tagIds`로 태그 연결
                    """)
    @PostMapping("/api/admin/gallery/albums")
    public ResponseEntity<GalleryAlbumDetailResponse> create(@Valid @RequestBody GalleryAlbumCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(albumService.create(request));
    }

    @Operation(summary = "앨범 수정", description = """
                    앨범 메타를 부분 수정한다(PATCH). 전달된(비-null) 필드만 적용한다.

                    - 인증(JWT): 필요 — `GALLERY_WRITE`
                    - 경로 변수: `id` — 수정할 앨범 ID
                    - 요청 본문: `GalleryAlbumPatchRequest` — `title`·`description`·`tagIds`(null이면 태그 미변경)·`version`(필수)
                    - 반환값: `GalleryAlbumDetailResponse` — 수정된 앨범 상세(`version` 증가분 반영)
                    - 부수효과: `version` 불일치 시 409 OPTIMISTIC_LOCK_CONFLICT (단, 태그만 변경하면 앨범 행 미변경이라 `version` 유지)
                    """)
    @PatchMapping("/api/admin/gallery/albums/{id}")
    public GalleryAlbumDetailResponse patch(
            @PathVariable Long id, @Valid @RequestBody GalleryAlbumPatchRequest request) {
        return albumService.patch(id, request);
    }

    @Operation(summary = "앨범 삭제", description = """
                    앨범을 soft delete 한다.

                    - 인증(JWT): 필요 — `GALLERY_WRITE`
                    - 경로 변수: `id` — 삭제할 앨범 ID
                    - 반환값: 없음(204)
                    - 부수효과: 앨범 soft delete + 태그 정리 · 소속 사진 연결(`gallery_photos`)은 함께 제거(연결해제)되나 media 원본은 라이브러리에 보존
                    """)
    @DeleteMapping("/api/admin/gallery/albums/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        albumService.delete(id);
    }

    @Operation(summary = "사진 추가", description = """
                    앨범에 사진을 추가한다. 신규 업로드와 기존 미디어 재사용을 함께 쓸 수 있다.

                    - 인증(JWT): 필요 — `GALLERY_WRITE`
                    - 경로 변수: `id` — 대상 앨범 ID
                    - 요청 본문/업로드: `files`(multipart, 신규 이미지 업로드)·`mediaIds`(기존 라이브러리 이미지 재사용) — 둘 중 하나 또는 혼합. 모두 이미지여야 함(`mediaIds`는 존재+이미지 검증, `files`는 매직바이트 이미지 검증)
                    - 반환값: `GalleryAlbumDetailResponse` — 갱신된 앨범 상세(추가된 사진 포함)
                    - 부수효과: `files` 업로드 시 한도(기본 10MB) 초과면 413 FILE_SIZE_EXCEEDED · media 재사용(`mediaIds`는 라이브러리 원본을 FK 연결) · 동시 추가는 앨범 행 비관락으로 직렬화(sort_order 경합 방지)
                    """)
    @PostMapping("/api/admin/gallery/albums/{id}/photos")
    public GalleryAlbumDetailResponse addPhotos(
            @PathVariable Long id,
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(required = false) List<Long> mediaIds,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return photoService.addPhotos(id, files, mediaIds, principal.id());
    }

    @Operation(summary = "사진 해제", description = """
                    앨범에서 사진을 해제한다(연결만 제거).

                    - 인증(JWT): 필요 — `GALLERY_WRITE`
                    - 경로 변수: `photoId` — 해제할 사진(연결) ID
                    - 반환값: 없음(204)
                    - 부수효과: 연결해제 — `gallery_photos` 행만 삭제하고 media 원본은 라이브러리에 보존(실제 파일 삭제는 미디어 차단형 삭제 API 사용)
                    """)
    @DeleteMapping("/api/admin/gallery/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePhoto(@PathVariable Long photoId) {
        photoService.removePhoto(photoId);
    }
}
