package com.elipair.church.global.common;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Page&lt;T&gt; VIA_DTO 직렬화 경로 검증용 테스트 전용 컨트롤러 */
@RestController
class PageTestController {

    @GetMapping("/test/page")
    Page<String> page() {
        return new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 10), 42);
    }
}
