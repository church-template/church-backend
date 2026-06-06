package com.elipair.church.domain.event;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 일정 동적 필터(스펙 §5.6). null 인자는 술어에서 제외. 항상 미삭제만(deletedAt IS NULL).
 * range 겹침은 end_at 배타: start_at < toExclusive AND (end_at > from OR (end_at IS NULL AND start_at >= from)).
 *   - end_at 배타라 경계 from과 같은 end_at은 제외(off-by-one 차단), null 점 이벤트는 start_at으로 포함.
 * taggedIds는 서비스가 미리 해석해 넘긴 id 목록 — 순수 조건 빌더로 유지.
 */
final class EventSpecifications {

    private EventSpecifications() {}

    static Specification<Event> filter(DateRange range, List<Long> taggedIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (range != null) {
                LocalDateTime from = range.from();
                LocalDateTime toExclusive = range.toExclusive();
                predicates.add(cb.lessThan(root.<LocalDateTime>get("startAt"), toExclusive));
                predicates.add(cb.or(
                        cb.greaterThan(root.<LocalDateTime>get("endAt"), from),
                        cb.and(
                                cb.isNull(root.get("endAt")),
                                cb.greaterThanOrEqualTo(root.<LocalDateTime>get("startAt"), from))));
            }
            if (taggedIds != null) {
                predicates.add(
                        taggedIds.isEmpty() ? cb.disjunction() : root.get("id").in(taggedIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
