package com.elipair.church.domain.gallery;

import com.elipair.church.domain.gallery.dto.GalleryAlbumCardResponse;
import com.elipair.church.domain.gallery.dto.GalleryAlbumDetailResponse;
import com.elipair.church.domain.visibility.ContentAccessGuard;
import com.elipair.church.domain.visibility.ContentDomain;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 갤러리 조회 API(스펙 §5.12). 열람 범위는 교회 설정을 따른다 — MEMBERS면 GALLERY_VIEW, ContentAccessGuard(#95).
 * 설정이 교회마다 DB에 있어 경로 매처·@PreAuthorize로는 표현할 수 없다.
 */
@Tag(
        name = "갤러리",
        description = "갤러리 앨범 조회 API(스펙 §5.12). 열람 범위는 교회 설정과 앨범 단위 `membersOnly`를 따른다(#95, ContentAccessGuard).")
@RestController
public class GalleryAlbumController {

    private final GalleryAlbumService service;
    private final ContentAccessGuard guard;

    public GalleryAlbumController(GalleryAlbumService service, ContentAccessGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @Operation(summary = "앨범 목록", description = """
                    갤러리 앨범 목록을 조회한다. 사진 이미지는 media 라이브러리를 FK로 재사용한다.

                    - 인증(JWT): 교회 설정(`GET /api/visibility`의 `gallery`)에 따름 — `PUBLIC`이면 불필요, `MEMBERS`면 `GALLERY_VIEW` 필요(비로그인 401 `INVALID_TOKEN`·권한 없음 403 `ACCESS_DENIED`). `Authorization`이 왔는데 무효·만료면 설정과 무관하게 401 `INVALID_TOKEN`(빈 `Bearer `는 토큰 없음으로 통과) · 메뉴가 `PUBLIC`이어도 `membersOnly=true` 앨범은 `GALLERY_VIEW`가 없으면 목록에서 빠진다
                    - 요청 파라미터: `tagId` — 태그 필터(생략 시 전체); `membersOnly` — 교인 전용 여부 필터(`GALLERY_WRITE` 보유 시에만 적용, 없으면 무시); `page`·`size`·`sort`(기본 `createdAt,desc`)
                    - 반환값: `Page<GalleryAlbumCardResponse>` — 카드 메타만(`thumbnailMediaId`·`thumbnailUrl`·`photoCount`·태그·작성자·`membersOnly`·`departmentLinked`·`version`); 본문 description·사진 목록 제외
                    """)
    @GetMapping("/api/gallery/albums")
    public Page<GalleryAlbumCardResponse> list(
            @RequestParam(required = false) Long tagId,
            @Parameter(description = "교인 전용 여부 필터 — GALLERY_WRITE 보유 시에만 적용(#95)") @RequestParam(required = false)
                    Boolean membersOnly,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        guard.requireMenu(ContentDomain.GALLERY);
        // 쓰기 권한이 없으면 필터를 무시한다 — 방문자가 totalElements로 교인 전용 앨범 수를 알아내지 못하게(설계 6.6-6)
        Boolean filter = guard.hasAuthority("GALLERY_WRITE") ? membersOnly : null;
        return service.list(tagId, guard.isMember(ContentDomain.GALLERY), filter, pageable);
    }

    @Operation(summary = "앨범 상세", description = """
                    앨범 상세와 소속 사진 목록을 조회한다.

                    - 인증(JWT): 교회 설정(`GET /api/visibility`의 `gallery`)에 따름 — `PUBLIC`이면 불필요, `MEMBERS`면 `GALLERY_VIEW` 필요(비로그인 401 `INVALID_TOKEN`·권한 없음 403 `ACCESS_DENIED`). `Authorization`이 왔는데 무효·만료면 설정과 무관하게 401 `INVALID_TOKEN`(빈 `Bearer `는 토큰 없음으로 통과) · `membersOnly=true` 앨범은 메뉴가 `PUBLIC`이어도 `GALLERY_VIEW` 필요(비로그인 401·권한 없음 403)
                    - 경로 변수: `id` — 조회할 앨범 ID
                    - 반환값: `GalleryAlbumDetailResponse` — 앨범 정보(description·태그·작성자·`version`·`membersOnly`·`departmentLinked`·`mediaUrls`) + 사진 목록(`GalleryPhotoResponse`: `mediaId`·서명 `url`, media 재사용 FK)
                    """)
    @GetMapping("/api/gallery/albums/{id}")
    public GalleryAlbumDetailResponse get(@PathVariable Long id) {
        guard.requireMenu(ContentDomain.GALLERY);
        guard.requireItem(ContentDomain.GALLERY, service.isMembersOnly(id));
        return service.get(id);
    }
}
