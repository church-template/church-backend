package com.elipair.church.domain.media;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** 미디어 라이브러리 동적 필터(스펙 §5.10). null 인자는 술어에서 제외 — 빈 술어는 전체 조회. */
final class MediaSpecifications {

    private MediaSpecifications() {}

    static Specification<Media> filter(String mimePrefix, LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (mimePrefix != null) {
                predicates.add(cb.like(root.get("mimeType"), mimePrefix));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
