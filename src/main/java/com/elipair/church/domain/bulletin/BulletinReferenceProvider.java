package com.elipair.church.domain.bulletin;

import com.elipair.church.domain.media.MediaReferenceProvider;
import com.elipair.church.domain.visibility.ContentDomain;
import com.elipair.church.domain.visibility.VisibilityService;
import com.elipair.church.global.common.ContentRef;
import com.elipair.church.global.tenant.TenantContext;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 주보 PDF FK(media_id) 참조 추적(스펙 §5.10 SPI). ContentRef.type="bulletin".
 * 활성 주보만 참조로 보고(soft-deleted 제외) media 차단삭제에 합류 — 갤러리 사진 Provider와 동일 구조.
 */
@Component
class BulletinReferenceProvider implements MediaReferenceProvider {

    private final BulletinRepository repository;
    private final VisibilityService visibility;

    BulletinReferenceProvider(BulletinRepository repository, VisibilityService visibility) {
        this.repository = repository;
        this.visibility = visibility;
    }

    /** 메뉴가 누구나일 때만 공개 근거가 된다 — 교인만으로 닫으면 주보 PDF도 함께 닫힌다(설계 6.2). */
    @Override
    public boolean hasPublicReference(long mediaId) {
        return visibility.isPublic(ContentDomain.BULLETIN)
                && !findReferences(mediaId).isEmpty();
    }

    @Override
    public List<ContentRef> findReferences(long mediaId) {
        return repository.findReferencesByMediaId(mediaId, TenantContext.require()).stream()
                .map(row -> new ContentRef("bulletin", row.getId(), row.getTitle()))
                .toList();
    }
}
