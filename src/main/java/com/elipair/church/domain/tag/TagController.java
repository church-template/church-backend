package com.elipair.church.domain.tag;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 태그 공개 조회(스펙 §5.11). GET은 SecurityConfig anyRequest permitAll. 비페이징 평배열. */
@RestController
public class TagController {

    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    @GetMapping("/api/tags")
    public List<TagResponse> list() {
        return service.list();
    }
}
