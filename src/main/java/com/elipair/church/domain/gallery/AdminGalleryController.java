package com.elipair.church.domain.gallery;

import com.elipair.church.domain.gallery.dto.GalleryAlbumCreateRequest;
import com.elipair.church.domain.gallery.dto.GalleryAlbumDetailResponse;
import com.elipair.church.domain.gallery.dto.GalleryAlbumPatchRequest;
import com.elipair.church.global.security.MemberPrincipal;
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
@RestController
@PreAuthorize("hasAuthority('GALLERY_WRITE')")
public class AdminGalleryController {

    private final GalleryAlbumService albumService;
    private final GalleryPhotoService photoService;

    public AdminGalleryController(GalleryAlbumService albumService, GalleryPhotoService photoService) {
        this.albumService = albumService;
        this.photoService = photoService;
    }

    @PostMapping("/api/admin/gallery/albums")
    public ResponseEntity<GalleryAlbumDetailResponse> create(@Valid @RequestBody GalleryAlbumCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(albumService.create(request));
    }

    @PatchMapping("/api/admin/gallery/albums/{id}")
    public GalleryAlbumDetailResponse patch(
            @PathVariable Long id, @Valid @RequestBody GalleryAlbumPatchRequest request) {
        return albumService.patch(id, request);
    }

    @DeleteMapping("/api/admin/gallery/albums/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        albumService.delete(id);
    }

    @PostMapping("/api/admin/gallery/albums/{id}/photos")
    public GalleryAlbumDetailResponse addPhotos(
            @PathVariable Long id,
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(required = false) List<Long> mediaIds,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return photoService.addPhotos(id, files, mediaIds, principal.id());
    }

    @DeleteMapping("/api/admin/gallery/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePhoto(@PathVariable Long photoId) {
        photoService.removePhoto(photoId);
    }
}
