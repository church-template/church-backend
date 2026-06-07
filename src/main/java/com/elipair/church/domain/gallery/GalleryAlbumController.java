package com.elipair.church.domain.gallery;

import com.elipair.church.domain.gallery.dto.GalleryAlbumCardResponse;
import com.elipair.church.domain.gallery.dto.GalleryAlbumDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 갤러리 회원 전용 조회 API(스펙 §5.12). 경로 /api/gallery/**는 SecurityConfig가 GALLERY_VIEW를 강제하므로
 * 메서드 @PreAuthorize는 두지 않는다(공개 조회 도메인이 경로 규칙에 의존하는 관례와 동일).
 */
@RestController
public class GalleryAlbumController {

    private final GalleryAlbumService service;

    public GalleryAlbumController(GalleryAlbumService service) {
        this.service = service;
    }

    @GetMapping("/api/gallery/albums")
    public Page<GalleryAlbumCardResponse> list(
            @RequestParam(required = false) Long tagId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(tagId, pageable);
    }

    @GetMapping("/api/gallery/albums/{id}")
    public GalleryAlbumDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
