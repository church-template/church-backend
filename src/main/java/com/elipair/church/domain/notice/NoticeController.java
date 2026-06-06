package com.elipair.church.domain.notice;

import com.elipair.church.domain.notice.dto.NoticeCardResponse;
import com.elipair.church.domain.notice.dto.NoticeDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공지 공개 조회 API(스펙 §5.7). 비인증 — SecurityConfig anyRequest permitAll. */
@RestController
public class NoticeController {

    private final NoticeService service;

    public NoticeController(NoticeService service) {
        this.service = service;
    }

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

    @GetMapping("/api/notices/{id}")
    public NoticeDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
