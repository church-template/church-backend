package com.elipair.church.domain.gallery;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 앨범 동적 필터(스펙 §5.12). 항상 미삭제만(deletedAt IS NULL). taggedIds는 서비스가 미리 해석해 넘긴 id 목록
 * (null=태그 필터 없음, 빈 리스트=해당 태그를 가진 앨범 없음 → 빈 결과).
 */
final class GalleryAlbumSpecifications {

    private GalleryAlbumSpecifications() {}

    static Specification<GalleryAlbum> filter(List<Long> taggedIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (taggedIds != null) {
                predicates.add(
                        taggedIds.isEmpty() ? cb.disjunction() : root.get("id").in(taggedIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
