package com.elipair.church.domain.media;

import com.elipair.church.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 중앙 미디어 라이브러리의 단일 파일 레코드(스펙 §5.10). 이미지·PDF를 mime_type으로 구분한다.
 * 업로드 후 불변(수정 없음). created_at만 감사하고 soft delete/version은 두지 않는다 — 차단형 하드 삭제 도메인.
 */
@Entity
@Table(name = "media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Media extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "stored_path", nullable = false, unique = true)
    private String storedPath;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private Long size;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private Long uploadedBy;

    private Media(String filename, String storedPath, String mimeType, Long size, Long uploadedBy) {
        this.filename = filename;
        this.storedPath = storedPath;
        this.mimeType = mimeType;
        this.size = size;
        this.uploadedBy = uploadedBy;
    }

    public static Media create(String filename, String storedPath, String mimeType, Long size, Long uploadedBy) {
        return new Media(filename, storedPath, mimeType, size, uploadedBy);
    }
}
