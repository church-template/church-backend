package com.elipair.church.domain.gallery;

import com.elipair.church.domain.gallery.dto.GalleryAlbumCardResponse;
import com.elipair.church.domain.gallery.dto.GalleryAlbumDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(
        name = "갤러리",
        description = "갤러리 앨범 조회 API(스펙 §5.12). 회원 전용(GALLERY_VIEW). /api/gallery/** 경로는 SecurityConfig가 일괄 적용.")
@RestController
public class GalleryAlbumController {

    private final GalleryAlbumService service;

    public GalleryAlbumController(GalleryAlbumService service) {
        this.service = service;
    }

    @Operation(summary = "앨범 목록(회원전용)", description = """
                    갤러리 앨범 목록을 조회한다(교인 전용). 사진 이미지는 media 라이브러리를 FK로 재사용한다.

                    - 인증(JWT): 필요 — 로그인 + `GALLERY_VIEW` (`MEMBER`=교인 승인자만 보유; `USER`·익명 차단)
                    - 요청 파라미터: `tagId` — 태그 필터(생략 시 전체); `page`·`size`·`sort`(기본 `createdAt,desc`)
                    - 반환값: `Page<GalleryAlbumCardResponse>` — 카드 메타만(`thumbnailMediaId`·`photoCount`·태그·작성자); 본문 description·사진 목록 제외
                    """)
    @GetMapping("/api/gallery/albums")
    public Page<GalleryAlbumCardResponse> list(
            @RequestParam(required = false) Long tagId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(tagId, pageable);
    }

    @Operation(summary = "앨범 상세", description = """
                    앨범 상세와 소속 사진 목록을 조회한다(교인 전용).

                    - 인증(JWT): 필요 — 로그인 + `GALLERY_VIEW`
                    - 경로 변수: `id` — 조회할 앨범 ID
                    - 반환값: `GalleryAlbumDetailResponse` — 앨범 정보(description·태그·작성자·`version`) + 사진 목록(`GalleryPhotoResponse`: `mediaId` 등, media 재사용 FK)
                    """)
    @GetMapping("/api/gallery/albums/{id}")
    public GalleryAlbumDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
