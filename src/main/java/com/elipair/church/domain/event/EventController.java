package com.elipair.church.domain.event;

import com.elipair.church.domain.event.dto.EventCardResponse;
import com.elipair.church.domain.event.dto.EventDetailResponse;
import com.elipair.church.domain.visibility.ContentAccessGuard;
import com.elipair.church.domain.visibility.ContentDomain;
import io.swagger.v3.oas.annotations.Operation;
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

/** 일정 조회 API(스펙 §5.6). 열람 범위는 교회 설정을 따른다 — ContentAccessGuard(#95). */
@Tag(name = "일정", description = "일정 조회/관리 API(스펙 §5.6). 열람 범위는 교회 설정을 따른다(#95).")
@RestController
public class EventController {

    private final EventService service;
    private final ContentAccessGuard guard;

    public EventController(EventService service, ContentAccessGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @Operation(summary = "일정 목록(달력)", description = """
                    달력 범위로 일정을 조회한다. 지정 구간과 겹치는 일정을 반환(end_at 배타 경계).

                    - 인증(JWT): 교회 설정(`GET /api/visibility`의 `events`)에 따름 — `PUBLIC`이면 불필요, `MEMBERS`면 `EVENT_VIEW` 필요(비로그인 401 `INVALID_TOKEN`·권한 없음 403 `ACCESS_DENIED`). `Authorization`이 왔는데 무효·만료면 설정과 무관하게 401 `INVALID_TOKEN`(빈 `Bearer `는 토큰 없음으로 통과)
                    - 요청 파라미터: `year`+`month` — 해당 월 전체(쌍으로); `startDate`+`endDate` — 직접 지정 범위(yyyy-MM-dd, 양끝 포함, 쌍으로); `tagId` — 태그 필터; 모두 없으면 전체. 쌍 누락·잘못된 범위는 400 · 동시 제공 시 year/month 우선
                    - 반환값: `EventCardResponse` — 카드 메타만(description 제외; 제목·장소·기간·종일·태그), 페이지네이션, start_at ASC 정렬
                    """)
    @GetMapping("/api/events")
    public Page<EventCardResponse> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long tagId,
            @PageableDefault(size = 10, sort = "startAt", direction = Sort.Direction.ASC) Pageable pageable) {
        guard.requireMenu(ContentDomain.EVENT);
        DateRange range = DateRange.resolve(year, month, startDate, endDate);
        return service.list(range, tagId, pageable);
    }

    @Operation(summary = "일정 상세", description = """
                    단일 일정 상세를 조회한다.

                    - 인증(JWT): 교회 설정(`GET /api/visibility`의 `events`)에 따름 — `PUBLIC`이면 불필요, `MEMBERS`면 `EVENT_VIEW` 필요(비로그인 401 `INVALID_TOKEN`·권한 없음 403 `ACCESS_DENIED`). `Authorization`이 왔는데 무효·만료면 설정과 무관하게 401 `INVALID_TOKEN`(빈 `Bearer `는 토큰 없음으로 통과)
                    - 경로 변수: `id` — 일정 ID
                    - 반환값: `EventDetailResponse` — description·기간·종일·`version`·태그 · `mediaUrls`(description의 `media:{id}`별 서명 주소, 없으면 빈 맵)
                    """)
    @GetMapping("/api/events/{id}")
    public EventDetailResponse get(@PathVariable Long id) {
        guard.requireMenu(ContentDomain.EVENT);
        return service.get(id);
    }
}
