package com.elipair.church.domain.visibility;

import com.elipair.church.domain.visibility.dto.VisibilityResponse;
import com.elipair.church.domain.visibility.dto.VisibilityUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 메뉴 공개 범위 설정(#95). /api/admin/** 인증 + VISIBILITY_MANAGE 메서드 보안. */
@Tag(name = "공개 범위")
@RestController
public class AdminVisibilityController {

    private final VisibilityService service;

    public AdminVisibilityController(VisibilityService service) {
        this.service = service;
    }

    @Operation(summary = "메뉴 공개 범위 저장", description = """
                    메뉴 5개의 공개 범위를 한 번에 덮어쓴다(마지막 저장이 이긴다 — 낙관락 없음).

                    - 인증(JWT): 필요 — `VISIBILITY_MANAGE`
                    - 요청 본문: `VisibilityUpdateRequest` — 5개 값 모두 필수(`PUBLIC`·`MEMBERS`), 빠지거나 다른 값이면 400
                    - 반환값: `VisibilityResponse` — 저장된 값
                    - 부수효과: 설정 캐시 갱신, 홈(`/api/main`) 캐시 무효화
                    """)
    @PutMapping("/api/admin/visibility")
    @PreAuthorize("hasAuthority('VISIBILITY_MANAGE')")
    public VisibilityResponse update(@Valid @RequestBody VisibilityUpdateRequest request) {
        return service.update(request);
    }
}
