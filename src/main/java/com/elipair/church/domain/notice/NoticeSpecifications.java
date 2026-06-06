package com.elipair.church.domain.notice;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * 공지 동적 필터(스펙 §5.7). null 인자는 술어에서 제외. 항상 미삭제만(deletedAt IS NULL).
 * q는 title만 검색(공지 카드 메타 텍스트가 title뿐). taggedIds는 서비스가 미리 해석해 넘긴 id 목록 — 순수 조건 빌더로 유지.
 */
final class NoticeSpecifications {

    private NoticeSpecifications() {}

    static Specification<Notice> filter(String q, List<Long> taggedIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), like));
            }
            if (taggedIds != null) {
                predicates.add(
                        taggedIds.isEmpty() ? cb.disjunction() : root.get("id").in(taggedIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
