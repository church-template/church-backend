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
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 파일 서빙(스펙 §5.10): 본문 이미지 렌더·PDF 열람용. 인증 불필요(경로 3분법의 공개 갈래).
 * mime_type 그대로 Content-Type을 세팅하되, nosniff로 브라우저 MIME 스니핑 기반 저장형 XSS를 차단한다.
 */
@Tag(name = "미디어", description = "미디어 파일 공개 서빙 API(스펙 §5.10)")
@RestController
public class MediaController {

    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    @Operation(summary = "파일 서빙(공개)", description = """
                    미디어 파일 원본을 그대로 내려보낸다(본문 이미지 렌더·PDF 열람용). 본문이 참조하는 `media:{id}`의 실제 바이트를 받는 경로다.

                    - 인증(JWT): 불필요 (경로 3분법의 공개 갈래)
                    - 경로 변수: `id` — 서빙할 미디어 ID
                    - 반환값: 파일 바이트(`Resource`). 저장된 `mime_type`을 그대로 Content-Type으로 세팅, `Content-Disposition: inline`(원본 파일명)
                    - 부수효과: `X-Content-Type-Options: nosniff`로 브라우저 MIME 스니핑 기반 저장형 XSS 차단 · `stored_path`는 외부에 비노출
                    """)
    @GetMapping("/api/media/{id}")
    public ResponseEntity<Resource> serve(@PathVariable Long id) {
        MediaContent content = service.serve(id);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.resource());
    }
}
