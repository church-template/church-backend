package com.elipair.church.domain.gallery;

import com.elipair.church.domain.media.MediaReferenceProvider;
import com.elipair.church.domain.visibility.ContentDomain;
import com.elipair.church.domain.visibility.VisibilityService;
import com.elipair.church.global.common.ContentRef;
import com.elipair.church.global.tenant.TenantContext;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 사진 FK(media_id) 참조 추적(스펙 §5.10 SPI) — 프로젝트 최초의 FK 기반 Provider. ContentRef.type="gallery_photo".
 * 사진 참조는 소속 앨범(id·title)으로 표면화하며, soft-deleted 앨범은 조인 필터로 제외(설계 §3).
 */
@Component
class GalleryPhotoReferenceProvider implements MediaReferenceProvider {

    private final GalleryPhotoRepository repository;
    private final VisibilityService visibility;

    GalleryPhotoReferenceProvider(GalleryPhotoRepository repository, VisibilityService visibility) {
        this.repository = repository;
        this.visibility = visibility;
    }

    /** 메뉴가 누구나이고 그 글이 "교인만"이 아닐 때만 공개 근거(설계 6.2). 메뉴를 먼저 봐 닫힌 메뉴는 쿼리를 건너뛴다. */
    @Override
    public boolean hasPublicReference(long mediaId) {
        return visibility.isPublic(ContentDomain.GALLERY)
                && repository.existsPublicReferenceByMediaId(mediaId, TenantContext.require());
    }

    @Override
    public List<ContentRef> findReferences(long mediaId) {
        return repository.findReferencesByMediaId(mediaId, TenantContext.require()).stream()
                .map(row -> new ContentRef("gallery_photo", row.getId(), row.getTitle()))
                .toList();
    }
}
