package com.elipair.church.domain.gallery;

import com.elipair.church.global.common.BaseEntity;
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
 * 갤러리 앨범(스펙 §5.12). 수정가능 콘텐츠라 BaseEntity(감사·소프트삭제·낙관락)를 상속.
 * 스펙은 PATCH만(PUT 없음)이라 부분 수정 applyPatch만 둔다. 작성자 표시는 updated_by(설계 §1).
 */
@Entity
@Table(name = "gallery_albums")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GalleryAlbum extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private GalleryAlbum(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public static GalleryAlbum create(String title, String description) {
        return new GalleryAlbum(title, description);
    }

    /** PATCH 부분 수정 — null 인자는 미변경. */
    public void applyPatch(String title, String description) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
    }
}
