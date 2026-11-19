package com.elipair.church.domain.sermon;

import com.elipair.church.domain.sermon.dto.SermonCardResponse;
import com.elipair.church.domain.sermon.dto.SermonDetailResponse;
import com.elipair.church.domain.visibility.ContentAccessGuard;
import com.elipair.church.domain.visibility.ContentDomain;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 설교 조회 API(스펙 §5.5). 열람 범위는 교회 설정을 따른다 — MEMBERS면 SERMON_VIEW, ContentAccessGuard(#95). */
@Tag(name = "설교", description = "설교 조회/관리 API(스펙 §5.5). 열람 범위는 교회 설정과 글 단위 `membersOnly`를 따른다(#95).")
@RestController
public class SermonController {

    private final SermonService service;
    private final ContentAccessGuard guard;

    public SermonController(SermonService service, ContentAccessGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @Operation(summary = "설교 목록", description = """
                    설교 카드 목록을 필터·검색·페이지네이션으로 조회한다.

                    - 인증(JWT): 교회 설정(`GET /api/visibility`의 `sermons`)에 따름 — `PUBLIC`이면 불필요, `MEMBERS`면 `SERMON_VIEW` 필요(비로그인 401 `INVALID_TOKEN`·권한 없음 403 `ACCESS_DENIED`). `Authorization`이 왔는데 무효·만료면 설정과 무관하게 401 `INVALID_TOKEN`(빈 `Bearer `는 토큰 없음으로 통과) · 메뉴가 `PUBLIC`이어도 `membersOnly=true` 설교는 `SERMON_VIEW`가 없으면 목록에서 빠진다
                    - 요청 파라미터: `preacher`·`series` — 설교자/시리즈 필터; `from`·`to` — 설교일 범위(yyyy-MM-dd); `q` — 제목/내용 검색어; `tagId` — 태그 필터; `membersOnly` — 교인 전용 여부 필터(`SERMON_WRITE` 보유 시에만 적용, 없으면 무시); `page`·`size`·`sort` — 페이지네이션(기본 `preachedAt,desc`)
                    - 반환값: `Page<SermonCardResponse>` — 카드 메타만(본문 `content` 제외, `membersOnly`·`version` 포함)·페이지네이션
                    """)
    @GetMapping("/api/sermons")
    public Page<SermonCardResponse> list(
            @Parameter(description = "설교자 필터") @RequestParam(required = false) String preacher,
            @Parameter(description = "시리즈 필터") @RequestParam(required = false) String series,
            @Parameter(description = "시작일 필터(yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "종료일 필터(yyyy-MM-dd)")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @Parameter(description = "제목/내용 검색어") @RequestParam(required = false) String q,
            @Parameter(description = "태그 ID 필터") @RequestParam(required = false) Long tagId,
            @Parameter(description = "교인 전용 여부 필터 — SERMON_WRITE 보유 시에만 적용(#95)") @RequestParam(required = false)
                    Boolean membersOnly,
            @PageableDefault(size = 10, sort = "preachedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        guard.requireMenu(ContentDomain.SERMON);
        // 쓰기 권한이 없으면 필터를 무시한다 — 방문자가 totalElements로 교인 전용 글 수를 알아내지 못하게(설계 6.6-6)
        Boolean filter = guard.hasAuthority("SERMON_WRITE") ? membersOnly : null;
        return service.list(
                preacher, series, from, to, q, tagId, guard.isMember(ContentDomain.SERMON), filter, pageable);
    }

    @Operation(summary = "설교 상세", description = """
                    설교 한 건의 상세를 조회한다(본문·태그·`version` 포함).

                    - 인증(JWT): 교회 설정(`GET /api/visibility`의 `sermons`)에 따름 — `PUBLIC`이면 불필요, `MEMBERS`면 `SERMON_VIEW` 필요(비로그인 401 `INVALID_TOKEN`·권한 없음 403 `ACCESS_DENIED`). `Authorization`이 왔는데 무효·만료면 설정과 무관하게 401 `INVALID_TOKEN`(빈 `Bearer `는 토큰 없음으로 통과) · `membersOnly=true` 설교는 메뉴가 `PUBLIC`이어도 `SERMON_VIEW` 필요(비로그인 401·권한 없음 403, 조회수 증가 전에 막는다)
                    - 경로 변수: `id` — 조회할 설교 ID
                    - 반환값: `SermonDetailResponse` — 본문 `content`·`membersOnly`·`mediaUrls`(본문 `media:{id}`별 서명 주소, 같은 교회에 없는 id는 빠짐, 없으면 빈 맵) 포함 상세
                    - 부수효과: 조회수 버퍼 +1(버퍼 누적분을 합산해 응답)
                    """)
    @GetMapping("/api/sermons/{id}")
    public SermonDetailResponse get(@PathVariable Long id) {
        guard.requireMenu(ContentDomain.SERMON);
        // 조회수를 올리는 get보다 먼저 막는다(설계 6.6-5)
        guard.requireItem(ContentDomain.SERMON, service.isMembersOnly(id));
        return service.get(id);
    }
}
