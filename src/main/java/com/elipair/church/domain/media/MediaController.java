package com.elipair.church.domain.media;

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
@RestController
public class MediaController {

    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

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
