package com.elipair.church.domain.sermon;

import com.elipair.church.domain.sermon.dto.SermonCardResponse;
import com.elipair.church.domain.sermon.dto.SermonDetailResponse;
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

/** 설교 공개 조회 API(스펙 §5.5). 비인증 — SecurityConfig anyRequest permitAll. */
@RestController
public class SermonController {

    private final SermonService service;

    public SermonController(SermonService service) {
        this.service = service;
    }

    @GetMapping("/api/sermons")
    public Page<SermonCardResponse> list(
            @RequestParam(required = false) String preacher,
            @RequestParam(required = false) String series,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long tagId,
            @PageableDefault(size = 10, sort = "preachedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(preacher, series, from, to, q, tagId, pageable);
    }

    @GetMapping("/api/sermons/{id}")
    public SermonDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
