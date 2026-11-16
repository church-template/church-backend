package com.elipair.church.domain.media.dto;

import com.elipair.church.domain.media.Media;
import java.time.LocalDateTime;

/** 미디어 단건·목록 카드 응답(스펙 §5.10). url=서명 주소(관리 화면 미리보기용, stored_path 비노출). */
public record MediaResponse(
        Long id, String filename, String mimeType, long size, Long uploadedBy, LocalDateTime createdAt, String url) {

    public static MediaResponse from(Media media, String url) {
        return new MediaResponse(
                media.getId(),
                media.getFilename(),
                media.getMimeType(),
                media.getSize(),
                media.getUploadedBy(),
                media.getCreatedAt(),
                url);
    }
}
