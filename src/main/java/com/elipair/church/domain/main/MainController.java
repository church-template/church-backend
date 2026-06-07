package com.elipair.church.domain.main;

import com.elipair.church.domain.main.dto.MainResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 메인페이지 통합 조회 API(스펙 §5.9). 공개 — SecurityConfig anyRequest permitAll. */
@Tag(name = "메인")
@RestController
public class MainController {

    private final MainService service;

    public MainController(MainService service) {
        this.service = service;
    }

    @Operation(summary = "메인 통합", description = "공개. 최신 설교 3·공지 3·다가오는 일정 5. Redis 캐싱.")
    @GetMapping("/api/main")
    public MainResponse main() {
        return service.getMain();
    }
}
