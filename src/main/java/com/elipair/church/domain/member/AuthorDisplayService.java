package com.elipair.church.domain.member;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * updated_by(회원 id) → 표시 이름 해석. 콘텐츠 도메인(sermon/notice/...)이 작성자 표시에 재사용(스펙 §5 작성자 정책).
 * soft-deleted 회원은 "(탈퇴한 사용자)"로 마스킹(FK 유지, 이름만 가림). id null/미존재는 "(알 수 없음)".
 * ContentTagService와 동일한 도메인 횡단 재사용 컴포넌트 — 전 메서드 public.
 */
@Service
@Transactional(readOnly = true)
public class AuthorDisplayService {

    static final String WITHDRAWN = "(탈퇴한 사용자)";
    public static final String UNKNOWN = "(알 수 없음)";

    private final MemberRepository memberRepository;

    public AuthorDisplayService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** 단건(상세용). */
    public String displayName(Long memberId) {
        if (memberId == null) {
            return UNKNOWN;
        }
        return displayNames(List.of(memberId)).get(memberId);
    }

    /** 배치(목록 N+1 회피). 요청한 모든(비-null) id를 키로 갖는 완전한 맵. */
    public Map<Long, String> displayNames(Collection<Long> memberIds) {
        Map<Long, String> result = new LinkedHashMap<>();
        if (memberIds == null) {
            return result;
        }
        for (Long id : memberIds) {
            if (id != null) {
                result.put(id, UNKNOWN); // 미존재 기본값
            }
        }
        if (result.isEmpty()) {
            return result;
        }
        for (AuthorView view : memberRepository.findAuthorViewsByIdIn(result.keySet())) {
            result.put(view.getId(), view.getDeletedAt() == null ? view.getName() : WITHDRAWN);
        }
        return result;
    }
}
