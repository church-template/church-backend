package com.elipair.church.domain.visibility;

import com.elipair.church.domain.visibility.dto.VisibilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 메뉴 공개 범위 공개 조회(#95). 프론트가 메뉴별로 게이트를 켤지 정하는 근거라 누구나 읽는다. */
@Tag(name = "공개 범위", description = "메뉴(공지·일정·주보·설교·갤러리)별 공개 범위 조회/설정 API(#95)")
@RestController
public class VisibilityController {

    private final VisibilityService service;

    public VisibilityController(VisibilityService service) {
        this.service = service;
    }

    @Operation(summary = "메뉴 공개 범위", description = """
                    이 교회의 메뉴 5개 공개 범위를 조회한다.

                    - 인증(JWT): 불필요
                    - 반환값: `VisibilityResponse` — `notices`·`events`·`bulletins`·`sermons`·`gallery` 각 `PUBLIC`(누구나)·`MEMBERS`(교인만)
                    """)
    @GetMapping("/api/visibility")
    public VisibilityResponse get() {
        return service.get();
    }
}
