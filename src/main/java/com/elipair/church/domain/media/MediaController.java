package com.elipair.church.domain.media;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 파일 서빙(스펙 §5.10): 본문 이미지 렌더·PDF 열람용. JWT 불필요(경로 3분법의 공개 갈래)이며, 공개 근거가 없는 미디어는 서명 주소로만 연다.
 * mime_type 그대로 Content-Type을 세팅하되, nosniff로 브라우저 MIME 스니핑 기반 저장형 XSS를 차단한다.
 */
@Tag(name = "미디어", description = "미디어 파일 공개 서빙 API(스펙 §5.10)")
@RestController
public class MediaController {

    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    @Operation(summary = "파일 서빙", description = """
                    미디어 파일 원본을 그대로 내려보낸다(본문 이미지 렌더·PDF 열람용). 본문이 참조하는 `media:{id}`의 실제 바이트를 받는 경로다.

                    - 인증(JWT): 불필요. 대신 접근 규칙이 있다
                      - 공지·행사·부서(대표 사진 포함)·주보에 쓰인 미디어는 공개 — 익명 200, `Cache-Control: public, max-age=86400`
                      - 그 외(설교·갤러리에만 쓰인 것 등)는 서명 주소(`exp`·`sig`)로만 200, `private, max-age=<남은 초>`. 서명 주소는 갤러리·관리자 응답이 내려준다
                      - 서명이 없거나 틀리거나 만료됐거나 둘 중 하나만 있으면 `404 RESOURCE_NOT_FOUND`(존재 여부를 드러내지 않는다). 단 서버 설정 `media.access-enforced=false`(기본)이면 서명 없는 비공개 요청도 200이되 `private, max-age=86400`
                    - 경로 변수: `id` — 서빙할 미디어 ID. 쿼리: `exp`(만료 epoch초)·`sig`(서명) — 서명 주소에서만
                    - 반환값: 파일 바이트(`Resource`). 저장된 `mime_type`을 그대로 Content-Type으로 세팅, `Content-Disposition: inline`(원본 파일명)
                    - 부수효과: `X-Content-Type-Options: nosniff`로 브라우저 MIME 스니핑 기반 저장형 XSS 차단 · `stored_path`는 외부에 비노출
                    """)
    @GetMapping("/api/media/{id}")
    public ResponseEntity<Resource> serve(
            @PathVariable Long id,
            @RequestParam(required = false) Long exp,
            @RequestParam(required = false) String sig) {
        MediaContent content = service.serve(id, exp, sig);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, content.cacheControl())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.resource());
    }
}
