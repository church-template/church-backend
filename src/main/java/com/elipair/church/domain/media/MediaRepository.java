package com.elipair.church.domain.media;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MediaRepository extends JpaRepository<Media, Long>, JpaSpecificationExecutor<Media> {

    /**
     * 라이브러리 그리드 검색(스펙 §5.10): 타입(mime 접두) + 생성일 범위 필터, 페이징.
     * 각 인자가 null이면 해당 조건은 술어에서 빠진다(Specification이 필요한 조건만 SQL로 생성 —
     * ":param is null" 형태의 타입 추론 불가 문제 회피). 정렬은 Pageable이 결정(기본 created_at DESC).
     */
    default Page<Media> search(String mimePrefix, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return findAll(MediaSpecifications.filter(mimePrefix, from, to), pageable);
    }
}
