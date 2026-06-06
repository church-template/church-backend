package com.elipair.church.domain.event;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일정/행사(스펙 §5.6). 수정가능 콘텐츠라 BaseEntity(감사·소프트삭제·낙관락)를 상속.
 * 조회수 없음(스펙 §5.6·§9). created_by/updated_by는 AuditorAware가 자동 주입하되 응답엔 미노출(설계 §1).
 * end_at은 배타(exclusive) 종료, null이면 점 이벤트.
 */
@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 200)
    private String location;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    private Event(
            String title,
            String description,
            String location,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
    }

    public static Event create(
            String title,
            String description,
            String location,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay) {
        return new Event(title, description, location, startAt, endAt, allDay);
    }

    /** PUT 전체 교체 — 감사필드 제외 전 필드를 요청값으로 덮어쓴다. */
    public void update(
            String title,
            String description,
            String location,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
    }

    /** PATCH 부분 수정 — null 인자는 미변경(end_at 비우기는 PUT 사용). */
    public void applyPatch(
            String title,
            String description,
            String location,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Boolean allDay) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (location != null) {
            this.location = location;
        }
        if (startAt != null) {
            this.startAt = startAt;
        }
        if (endAt != null) {
            this.endAt = endAt;
        }
        if (allDay != null) {
            this.allDay = allDay;
        }
    }
}
