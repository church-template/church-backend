package com.elipair.church.domain.member;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * 회원 검색 동적 필터. null/blank q는 술어 제외. 항상 미삭제만(deletedAt IS NULL).
 * q는 이름(부분일치, 대소문자 무시) 또는 전화번호(숫자 정규화 후 부분일치)에 OR 매칭.
 * q에 숫자가 없으면 전화 술어를 붙이지 않는다(이름 검색의 전화 헛매칭 방지).
 */
final class MemberSpecifications {

    private MemberSpecifications() {}

    static Specification<Member> filter(String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (q != null && !q.isBlank()) {
                String normalizedQ = q.trim();
                List<Predicate> match = new ArrayList<>();
                match.add(cb.like(cb.lower(root.get("name")), "%" + normalizedQ.toLowerCase(Locale.ROOT) + "%"));
                String digits = PhoneNumbers.extractDigits(normalizedQ);
                if (!digits.isEmpty()) {
                    match.add(cb.like(root.get("phone"), "%" + digits + "%"));
                }
                predicates.add(cb.or(match.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
