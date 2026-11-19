package com.elipair.church.domain.bulletin;

import com.elipair.church.domain.bulletin.dto.BulletinCardResponse;
import com.elipair.church.domain.bulletin.dto.BulletinDetailResponse;
import com.elipair.church.domain.visibility.ContentAccessGuard;
import com.elipair.church.domain.visibility.ContentDomain;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 주보 조회 API(스펙 §5.13). 열람 범위는 교회 설정을 따른다 — ContentAccessGuard(#95). */
@Tag(name = "주보", description = "주보 조회/관리 API(스펙 §5.13). 열람 범위는 교회 설정을 따른다(#95). PDF media 재사용 기반.")
@RestController
public class BulletinController {

    private final BulletinService service;
    private final ContentAccessGuard guard;

    public BulletinController(BulletinService service, ContentAccessGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @Operation(summary = "주보 목록", description = """
                    주보 목록을 조회한다. PDF는 media 라이브러리를 재사용한다.

                    - 인증(JWT): 교회 설정(`GET /api/visibility`의 `bulletins`)에 따름 — `PUBLIC`이면 불필요, `MEMBERS`면 `BULLETIN_VIEW` 필요(비로그인 401 `INVALID_TOKEN`·권한 없음 403 `ACCESS_DENIED`). `Authorization`이 왔는데 무효·만료면 설정과 무관하게 401 `INVALID_TOKEN`(빈 `Bearer `는 토큰 없음으로 통과)
                    - 요청 파라미터: `page`·`size`·`sort`(기본 `serviceDate,desc` — 예배일 내림차순)
                    - 반환값: `Page<BulletinCardResponse>` — 카드 메타(`title`·`serviceDate`·`mediaId`·작성자) + `url`(PDF 서명 주소, PDF 없으면 null)
                    """)
    @GetMapping("/api/bulletins")
    public Page<BulletinCardResponse> list(
            @PageableDefault(size = 10, sort = "serviceDate", direction = Sort.Direction.DESC) Pageable pageable) {
        guard.requireMenu(ContentDomain.BULLETIN);
        return service.list(pageable);
    }

    @Operation(summary = "주보 상세", description = """
                    주보 한 건의 상세를 조회한다.

                    - 인증(JWT): 교회 설정(`GET /api/visibility`의 `bulletins`)에 따름 — `PUBLIC`이면 불필요, `MEMBERS`면 `BULLETIN_VIEW` 필요(비로그인 401 `INVALID_TOKEN`·권한 없음 403 `ACCESS_DENIED`). `Authorization`이 왔는데 무효·만료면 설정과 무관하게 401 `INVALID_TOKEN`(빈 `Bearer `는 토큰 없음으로 통과)
                    - 경로 변수: `id` — 조회할 주보 ID
                    - 반환값: `BulletinDetailResponse` — `title`·`serviceDate`·`mediaId`·작성자·`version`·`url`(PDF 서명 주소 — 주보가 교인만이어도 열린다. 서명 없는 `/api/media/{mediaId}`는 메뉴가 `PUBLIC`일 때만 열림)
                    """)
    @GetMapping("/api/bulletins/{id}")
    public BulletinDetailResponse get(@PathVariable Long id) {
        guard.requireMenu(ContentDomain.BULLETIN);
        return service.get(id);
    }
}
