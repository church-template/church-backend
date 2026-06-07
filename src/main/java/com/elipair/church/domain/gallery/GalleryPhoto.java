package com.elipair.church.domain.gallery;

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
 * 갤러리 사진(스펙 §5.12) — 앨범↔media 연결행. 경량(BaseTimeEntity, created_at만): soft delete/version 없음.
 * 연결 해제는 hard delete(media 원본은 라이브러리에 보존). album_id/media_id는 평문 Long FK(저결합).
 * caption은 MVP에서 미사용(추가 시 null) — 후속 이슈에서 편집 추가(설계 §10).
 */
@Entity
@Table(name = "gallery_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GalleryPhoto extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "album_id", nullable = false)
    private Long albumId;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Column(length = 500)
    private String caption;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    private GalleryPhoto(Long albumId, Long mediaId, Integer sortOrder) {
        this.albumId = albumId;
        this.mediaId = mediaId;
        this.sortOrder = sortOrder;
    }

    public static GalleryPhoto create(Long albumId, Long mediaId, Integer sortOrder) {
        return new GalleryPhoto(albumId, mediaId, sortOrder);
    }
}
