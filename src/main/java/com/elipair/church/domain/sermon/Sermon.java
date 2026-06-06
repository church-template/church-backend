package com.elipair.church.domain.sermon;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설교(스펙 §5.5). 수정가능 콘텐츠라 BaseEntity(감사·소프트삭제·낙관락)를 상속하는 첫 엔티티.
 * viewCount는 앱 코드용 세터가 없다 — 오직 리포지토리 원자 쿼리로만 증가(@Version 미증가).
 * created_by/updated_by는 AuditorAware가 자동 주입(서비스 수동 세팅 안 함).
 */
@Entity
@Table(name = "sermons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sermon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 100)
    private String preacher;

    @Column(length = 100)
    private String series;

    @Column(length = 200)
    private String scripture;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "preached_at", nullable = false)
    private LocalDate preachedAt;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    private Sermon(
            String title,
            String preacher,
            String series,
            String scripture,
            String content,
            String videoUrl,
            String audioUrl,
            LocalDate preachedAt) {
        this.title = title;
        this.preacher = preacher;
        this.series = series;
        this.scripture = scripture;
        this.content = content;
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.preachedAt = preachedAt;
        this.viewCount = 0L;
    }

    public static Sermon create(
            String title,
            String preacher,
            String series,
            String scripture,
            String content,
            String videoUrl,
            String audioUrl,
            LocalDate preachedAt) {
        return new Sermon(title, preacher, series, scripture, content, videoUrl, audioUrl, preachedAt);
    }

    /** PUT 전체 교체 — viewCount·감사필드 제외 전 필드를 요청값으로 덮어쓴다(null이면 해당 컬럼 비움). */
    public void update(
            String title,
            String preacher,
            String series,
            String scripture,
            String content,
            String videoUrl,
            String audioUrl,
            LocalDate preachedAt) {
        this.title = title;
        this.preacher = preacher;
        this.series = series;
        this.scripture = scripture;
        this.content = content;
        this.videoUrl = videoUrl;
        this.audioUrl = audioUrl;
        this.preachedAt = preachedAt;
    }

    /** PATCH 부분 수정 — null 인자는 미변경(nullable 컬럼을 null로 '비우기'는 PUT으로). */
    public void applyPatch(
            String title,
            String preacher,
            String series,
            String scripture,
            String content,
            String videoUrl,
            String audioUrl,
            LocalDate preachedAt) {
        if (title != null) {
            this.title = title;
        }
        if (preacher != null) {
            this.preacher = preacher;
        }
        if (series != null) {
            this.series = series;
        }
        if (scripture != null) {
            this.scripture = scripture;
        }
        if (content != null) {
            this.content = content;
        }
        if (videoUrl != null) {
            this.videoUrl = videoUrl;
        }
        if (audioUrl != null) {
            this.audioUrl = audioUrl;
        }
        if (preachedAt != null) {
            this.preachedAt = preachedAt;
        }
    }
}
