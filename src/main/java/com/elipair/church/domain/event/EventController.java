package com.elipair.church.domain.event;

import com.elipair.church.domain.event.dto.EventCardResponse;
import com.elipair.church.domain.event.dto.EventDetailResponse;
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

/** 일정 공개 조회 API(스펙 §5.6). 비인증 — SecurityConfig anyRequest permitAll. */
@Tag(name = "일정", description = "일정 공개 조회/관리 API(스펙 §5.6)")
@RestController
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @Operation(
            summary = "일정 목록(달력)",
            description =
                    "공개. 카드 메타만(content 제외). 필터: year+month(해당 월 전체)·startDate+endDate(직접 지정, yyyy-MM-dd)·tagId. 필터 없으면 전체. start_at ASC 정렬.")
    @GetMapping("/api/events")
    public Page<EventCardResponse> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long tagId,
            @PageableDefault(size = 10, sort = "startAt", direction = Sort.Direction.ASC) Pageable pageable) {
        DateRange range = DateRange.resolve(year, month, startDate, endDate);
        return service.list(range, tagId, pageable);
    }

    @Operation(summary = "일정 상세", description = "공개. content 포함.")
    @GetMapping("/api/events/{id}")
    public EventDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
