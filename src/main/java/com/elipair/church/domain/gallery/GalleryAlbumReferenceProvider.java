package com.elipair.church.domain.gallery;

import com.elipair.church.domain.media.MediaReferenceProvider;
import com.elipair.church.domain.visibility.ContentDomain;
import com.elipair.church.domain.visibility.VisibilityService;
import com.elipair.church.global.common.ContentRef;
import com.elipair.church.global.tenant.TenantContext;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 앨범 본문(description) media:{id} 참조 추적(스펙 §5.10 SPI). ContentRef.type="gallery_album".
 * soft-deleted 앨범 제외. 경계 안전: media:42가 media:420/421에 오탐되지 않는다.
 */
@Component
class GalleryAlbumReferenceProvider implements MediaReferenceProvider {

    private final GalleryAlbumRepository repository;
    private final VisibilityService visibility;

    GalleryAlbumReferenceProvider(GalleryAlbumRepository repository, VisibilityService visibility) {
        this.repository = repository;
        this.visibility = visibility;
    }

    /** 메뉴가 누구나이고 그 글이 "교인만"이 아닐 때만 공개 근거(설계 6.2). 메뉴를 먼저 봐 닫힌 메뉴는 쿼리를 건너뛴다. */
    @Override
    public boolean hasPublicReference(long mediaId) {
        return visibility.isPublic(ContentDomain.GALLERY)
                && repository.existsPublicReferenceByMedia("media:" + mediaId + "($|[^0-9])", TenantContext.require());
    }

    @Override
    public List<ContentRef> findReferences(long mediaId) {
        String pattern = "media:" + mediaId + "($|[^0-9])";
        return repository.findReferencesByMedia(pattern, TenantContext.require()).stream()
                .map(row -> new ContentRef("gallery_album", row.getId(), row.getTitle()))
                .toList();
    }
}
