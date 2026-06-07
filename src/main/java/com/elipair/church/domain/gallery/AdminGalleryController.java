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

    @Operation(summary = "앨범 생성", description = "GALLERY_WRITE. 앨범 메타(제목·설명·태그) 생성. 사진은 별도 추가 엔드포인트 사용.")
    @PostMapping("/api/admin/gallery/albums")
    public ResponseEntity<GalleryAlbumDetailResponse> create(@Valid @RequestBody GalleryAlbumCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(albumService.create(request));
    }

    @Operation(summary = "앨범 수정", description = "GALLERY_WRITE. 앨범 메타 부분 수정. null 필드 미변경. 낙관락(version) 충돌 시 409.")
    @PatchMapping("/api/admin/gallery/albums/{id}")
    public GalleryAlbumDetailResponse patch(
            @PathVariable Long id, @Valid @RequestBody GalleryAlbumPatchRequest request) {
        return albumService.patch(id, request);
    }

    @Operation(
            summary = "앨범 삭제",
            description = "GALLERY_WRITE. soft delete. 소속 사진 연결(gallery_photos)은 함께 제거되나 media 원본은 보존된다.")
    @DeleteMapping("/api/admin/gallery/albums/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        albumService.delete(id);
    }

    @Operation(
            summary = "사진 추가",
            description =
                    "GALLERY_WRITE. multipart(files) 신규 업로드 또는 기존 mediaIds 재사용 중 하나, 또는 혼합 가능. media 라이브러리 재사용 원칙에 따라 mediaIds 우선 권장.")
    @PostMapping("/api/admin/gallery/albums/{id}/photos")
    public GalleryAlbumDetailResponse addPhotos(
            @PathVariable Long id,
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(required = false) List<Long> mediaIds,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return photoService.addPhotos(id, files, mediaIds, principal.id());
    }

    @Operation(
            summary = "사진 해제",
            description =
                    "GALLERY_WRITE. 앨범에서 사진 연결만 제거(gallery_photos 행 삭제). media 원본은 보존되므로 실제 파일 삭제는 미디어 삭제 API를 사용.")
    @DeleteMapping("/api/admin/gallery/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePhoto(@PathVariable Long photoId) {
        photoService.removePhoto(photoId);
    }
}
