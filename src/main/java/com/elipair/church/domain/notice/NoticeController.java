package com.elipair.church.domain.notice;

import com.elipair.church.domain.notice.dto.NoticeCardResponse;
import com.elipair.church.domain.notice.dto.NoticeDetailResponse;
import com.elipair.church.domain.visibility.ContentAccessGuard;
import com.elipair.church.domain.visibility.ContentDomain;
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

/** 공지 조회 API(스펙 §5.7). 열람 범위는 교회 설정을 따른다 — ContentAccessGuard(#95). */
@Tag(name = "공지", description = "공지 조회/관리 API(스펙 §5.7). 열람 범위는 교회 설정을 따른다(#95).")
@RestController
public class NoticeController {

    private final NoticeService service;
    private final ContentAccessGuard guard;

    public NoticeController(NoticeService service, ContentAccessGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @Operation(summary = "공지 목록", description = """
                    공지 카드 목록을 검색·페이지네이션으로 조회한다(고정글 우선 정렬).

                    - 인증(JWT): 교회 설정(`GET /api/visibility`의 `notices`)에 따름 — `PUBLIC`이면 불필요, `MEMBERS`면 `NOTICE_VIEW` 필요(비로그인 401 `INVALID_TOKEN`·권한 없음 403 `ACCESS_DENIED`). `Authorization`이 왔는데 무효·만료면 설정과 무관하게 401 `INVALID_TOKEN`(빈 `Bearer `는 토큰 없음으로 통과)
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
        guard.requireMenu(ContentDomain.NOTICE);
        return service.list(q, tagId, pageable);
    }

    @Operation(summary = "공지 상세", description = """
                    공지 한 건의 상세를 조회한다(본문·태그·`version` 포함).

                    - 인증(JWT): 교회 설정(`GET /api/visibility`의 `notices`)에 따름 — `PUBLIC`이면 불필요, `MEMBERS`면 `NOTICE_VIEW` 필요(비로그인 401 `INVALID_TOKEN`·권한 없음 403 `ACCESS_DENIED`). `Authorization`이 왔는데 무효·만료면 설정과 무관하게 401 `INVALID_TOKEN`(빈 `Bearer `는 토큰 없음으로 통과)
                    - 경로 변수: `id` — 조회할 공지 ID
                    - 반환값: `NoticeDetailResponse` — 본문 `content` 포함 상세 · `mediaUrls`(본문 `media:{id}`별 서명 주소, 같은 교회에 없는 id는 빠짐, 없으면 빈 맵)
                    - 부수효과: 조회수 버퍼 +1(버퍼 누적분을 합산해 응답)
                    """)
    @GetMapping("/api/notices/{id}")
    public NoticeDetailResponse get(@PathVariable Long id) {
        guard.requireMenu(ContentDomain.NOTICE);
        return service.get(id);
    }
}
