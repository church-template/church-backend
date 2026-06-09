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

    @Operation(summary = "메인 통합", description = """
                    메인페이지용 통합 조회. 최신 설교 3·공지 3·다가오는 일정 5를 한 번에 반환한다.

                    - 인증(JWT): 불필요
                    - 반환값: `MainResponse` — `sermons`·`notices`·`upcomingEvents` 카드 목록(본문 제외 메타만)
                    - 부수효과: Redis 캐싱(@Cacheable "main"); 설교/공지/일정 CUD 시 @CacheEvict로 무효화
                    """)
    @GetMapping("/api/main")
    public MainResponse main() {
        return service.getMain();
    }
}
