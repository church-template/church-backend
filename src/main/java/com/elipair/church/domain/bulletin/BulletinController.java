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

    @Operation(summary = "주보 목록", description = """
                    주보 목록을 조회한다. PDF는 media 라이브러리를 재사용한다.

                    - 인증(JWT): 불필요 (공개 조회)
                    - 요청 파라미터: `page`·`size`·`sort`(기본 `serviceDate,desc` — 예배일 내림차순)
                    - 반환값: `Page<BulletinCardResponse>` — 카드 메타만(`title`·`serviceDate`·`mediaId`·작성자); PDF 바이트/URL은 미포함
                    """)
    @GetMapping("/api/bulletins")
    public Page<BulletinCardResponse> list(
            @PageableDefault(size = 10, sort = "serviceDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(pageable);
    }

    @Operation(summary = "주보 상세", description = """
                    주보 한 건의 상세를 조회한다.

                    - 인증(JWT): 불필요 (공개 조회)
                    - 경로 변수: `id` — 조회할 주보 ID
                    - 반환값: `BulletinDetailResponse` — `title`·`serviceDate`·`mediaId`·작성자·`version`. 실제 PDF는 `GET /api/media/{mediaId}`로 접근(`bulletins.media_id` FK 재사용)
                    """)
    @GetMapping("/api/bulletins/{id}")
    public BulletinDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
