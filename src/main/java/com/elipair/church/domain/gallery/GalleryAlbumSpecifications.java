package com.elipair.church.domain.gallery;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 앨범 동적 필터(스펙 §5.12). 항상 미삭제만(deletedAt IS NULL). taggedIds는 서비스가 미리 해석해 넘긴 id 목록
 * (null=태그 필터 없음, 빈 리스트=해당 태그를 가진 앨범 없음 → 빈 결과).
 * includeMembersOnly=false면 교인 전용 앨범을 뺀다(보는 사람 판단은 컨트롤러가 해서 넘긴다, #95).
 * membersOnlyFilter는 관리자용 필터(null=무관).
 */
final class GalleryAlbumSpecifications {

    private GalleryAlbumSpecifications() {}

    static Specification<GalleryAlbum> filter(
            List<Long> taggedIds, boolean includeMembersOnly, Boolean membersOnlyFilter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (!includeMembersOnly) {
                predicates.add(cb.isFalse(root.get("membersOnly")));
            }
            if (membersOnlyFilter != null) {
                predicates.add(cb.equal(root.get("membersOnly"), membersOnlyFilter));
            }
            if (taggedIds != null) {
                predicates.add(
                        taggedIds.isEmpty() ? cb.disjunction() : root.get("id").in(taggedIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
