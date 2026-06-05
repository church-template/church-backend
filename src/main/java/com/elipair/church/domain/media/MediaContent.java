package com.elipair.church.domain.media;

import org.springframework.core.io.Resource;

/**
 * 공개 서빙(GET /api/media/{id})용 파일 콘텐츠 묶음. stored_path를 컨트롤러에 노출하지 않고
 * 서비스가 FileStorage로 로드한 Resource와 응답 헤더 재료(mimeType·filename)만 전달한다.
 */
public record MediaContent(Resource resource, String mimeType, String filename) {}
