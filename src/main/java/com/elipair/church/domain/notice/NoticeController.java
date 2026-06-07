package com.elipair.church.domain.notice;

import com.elipair.church.domain.notice.dto.NoticeCardResponse;
import com.elipair.church.domain.notice.dto.NoticeDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공지 공개 조회 API(스펙 §5.7). 비인증 — SecurityConfig anyRequest permitAll. */
@Tag(name = "공지", description = "공지 공개 조회/관리 API(스펙 §5.7)")
@RestController
public class NoticeController {

    private final NoticeService service;

    public NoticeController(NoticeService service) {
        this.service = service;
    }

    @Operation(summary = "공지 목록", description = "공개. 카드 메타만(content 제외). 고정글 우선 정렬. q 검색·태그 필터·페이지네이션.")
    @GetMapping("/api/notices")
    public Page<NoticeCardResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long tagId,
            @PageableDefault(
                            size = 10,
                            sort = {"isPinned", "createdAt"},
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return service.list(q, tagId, pageable);
    }

    @Operation(summary = "공지 상세", description = "공개. content 포함. 조회 시 view_count 버퍼 +1.")
    @GetMapping("/api/notices/{id}")
    public NoticeDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
