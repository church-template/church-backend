package com.elipair.church.domain.sermon;

import com.elipair.church.domain.sermon.dto.SermonCardResponse;
import com.elipair.church.domain.sermon.dto.SermonDetailResponse;
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

/** 설교 공개 조회 API(스펙 §5.5). 비인증 — SecurityConfig anyRequest permitAll. */
@Tag(name = "설교", description = "설교 공개 조회/관리 API(스펙 §5.5)")
@RestController
public class SermonController {

    private final SermonService service;

    public SermonController(SermonService service) {
        this.service = service;
    }

    @Operation(summary = "설교 목록", description = "공개. 카드 메타만(content 제외). 설교자·시리즈·날짜·태그 필터, q 검색, 페이지네이션.")
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
            @PageableDefault(size = 10, sort = "preachedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(preacher, series, from, to, q, tagId, pageable);
    }

    @Operation(summary = "설교 상세", description = "공개. content 포함. 조회 시 view_count 버퍼 +1.")
    @GetMapping("/api/sermons/{id}")
    public SermonDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
