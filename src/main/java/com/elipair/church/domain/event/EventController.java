package com.elipair.church.domain.event;

import com.elipair.church.domain.event.dto.EventCardResponse;
import com.elipair.church.domain.event.dto.EventDetailResponse;
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
@RestController
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

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

    @GetMapping("/api/events/{id}")
    public EventDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
