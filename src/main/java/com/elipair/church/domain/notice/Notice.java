package com.elipair.church.domain.notice;

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
 * 공지(스펙 §5.7). 수정가능 콘텐츠라 BaseEntity(감사·소프트삭제·낙관락)를 상속.
 * viewCount는 앱 코드용 세터가 없다 — 오직 리포지토리 원자 쿼리로만 증가(@Version 미증가).
 * created_by/updated_by는 AuditorAware가 자동 주입(서비스 수동 세팅 안 함).
 */
@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    private Notice(String title, String content, boolean isPinned) {
        this.title = title;
        this.content = content;
        this.isPinned = isPinned;
        this.viewCount = 0L;
    }

    public static Notice create(String title, String content, boolean isPinned) {
        return new Notice(title, content, isPinned);
    }

    /** PUT 전체 교체 — viewCount·감사필드 제외 전 필드를 요청값으로 덮어쓴다. */
    public void update(String title, String content, boolean isPinned) {
        this.title = title;
        this.content = content;
        this.isPinned = isPinned;
    }

    /** PATCH 부분 수정 — null 인자는 미변경(상단고정 토글 포함). */
    public void applyPatch(String title, String content, Boolean isPinned) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (isPinned != null) {
            this.isPinned = isPinned;
        }
    }
}
