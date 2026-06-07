package com.elipair.church.domain.bulletin;

import com.elipair.church.domain.bulletin.dto.BulletinCardResponse;
import com.elipair.church.domain.bulletin.dto.BulletinDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 주보 공개 조회 API(스펙 §5.13). 비인증 — SecurityConfig anyRequest permitAll. */
@Tag(name = "주보", description = "주보 공개 조회/관리 API(스펙 §5.13). PDF media 재사용 기반.")
@RestController
public class BulletinController {

    private final BulletinService service;

    public BulletinController(BulletinService service) {
        this.service = service;
    }

    @Operation(summary = "주보 목록", description = "공개. 예배일(serviceDate) 내림차순. 카드 메타만(PDF URL 제외). 페이지네이션.")
    @GetMapping("/api/bulletins")
    public Page<BulletinCardResponse> list(
            @PageableDefault(size = 10, sort = "serviceDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(pageable);
    }

    @Operation(
            summary = "주보 상세",
            description =
                    "공개. PDF는 media 라이브러리에 저장되며 bulletins.media_id FK로 참조. 실제 PDF 파일은 GET /api/media/{mediaId}로 접근.")
    @GetMapping("/api/bulletins/{id}")
    public BulletinDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
