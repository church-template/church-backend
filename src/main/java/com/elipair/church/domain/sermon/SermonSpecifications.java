package com.elipair.church.domain.sermon;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * 설교 동적 필터(스펙 §5.5). null 인자는 술어에서 제외. 항상 미삭제만(deletedAt IS NULL).
 * taggedIds는 서비스가 ContentTagService로 미리 해석해 넘긴 id 목록 — Specification은 순수 조건 빌더로 유지.
 */
final class SermonSpecifications {

    private SermonSpecifications() {}

    static Specification<Sermon> filter(
            String preacher, String series, LocalDate from, LocalDate to, String q, List<Long> taggedIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (preacher != null) {
                predicates.add(cb.equal(root.get("preacher"), preacher));
            }
            if (series != null) {
                predicates.add(cb.equal(root.get("series"), series));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("preachedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("preachedAt"), to));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("preacher")), like),
                        cb.like(cb.lower(root.get("series")), like),
                        cb.like(cb.lower(root.get("scripture")), like)));
            }
            if (taggedIds != null) {
                predicates.add(
                        taggedIds.isEmpty() ? cb.disjunction() : root.get("id").in(taggedIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
