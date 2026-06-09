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

    @Operation(summary = "공지 목록", description = """
                    공지 카드 목록을 검색·페이지네이션으로 조회한다(고정글 우선 정렬).

                    - 인증(JWT): 불필요
                    - 요청 파라미터: `q` — 제목 검색어(제목만 매칭); `tagId` — 태그 필터; `page`·`size`·`sort` — 페이지네이션(기본 `isPinned,createdAt` 모두 desc → 고정글 우선·최신순)
                    - 반환값: `Page<NoticeCardResponse>` — 카드 메타만(본문 `content` 제외)·페이지네이션
                    """)
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

    @Operation(summary = "공지 상세", description = """
                    공지 한 건의 상세를 조회한다(본문·태그·`version` 포함).

                    - 인증(JWT): 불필요
                    - 경로 변수: `id` — 조회할 공지 ID
                    - 반환값: `NoticeDetailResponse` — 본문 `content` 포함 상세
                    - 부수효과: 조회수 버퍼 +1(버퍼 누적분을 합산해 응답)
                    """)
    @GetMapping("/api/notices/{id}")
    public NoticeDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
