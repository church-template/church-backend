package com.elipair.church.domain.media.dto;

import com.elipair.church.domain.media.Media;
import java.time.LocalDateTime;

/** 미디어 단건·목록 카드 응답(스펙 §5.10). 실제 접근 URL은 프론트가 FILE_BASE_URL + id로 조립한다(stored_path 비노출). */
public record MediaResponse(
        Long id, String filename, String mimeType, long size, Long uploadedBy, LocalDateTime createdAt) {

    public static MediaResponse from(Media media) {
        return new MediaResponse(
                media.getId(),
                media.getFilename(),
                media.getMimeType(),
                media.getSize(),
                media.getUploadedBy(),
                media.getCreatedAt());
    }
}
