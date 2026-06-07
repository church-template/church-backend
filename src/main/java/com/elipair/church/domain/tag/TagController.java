package com.elipair.church.domain.tag;

import com.elipair.church.domain.tag.dto.TagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 태그 공개 조회(스펙 §5.11). GET은 SecurityConfig anyRequest permitAll. 비페이징 평배열. */
@Tag(
        name = "태그",
        description = "태그 공개 조회/관리 API(스펙 §5.11). 설교·공지·일정·부서에 다형 연결; 태그 삭제 시 해당 태그의 모든 연결(content_tags)이 정리됨.")
@RestController
public class TagController {

    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    @Operation(summary = "태그 목록", description = "공개. 전체 태그 비페이징 평배열.")
    @GetMapping("/api/tags")
    public List<TagResponse> list() {
        return service.list();
    }
}
