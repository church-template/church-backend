package com.elipair.church.domain.vehicle;

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
 * 차량 운행일(이슈 #62). 관리자가 사전 등록하고 승인 교인이 날짜별로 탑승을 신청한다.
 * departs_at이 신청 마감 시각을 겸한다 — 지나면 신청 불가. 운행 취소 = soft delete(신청은 이력 보존).
 */
@Entity
@Table(name = "vehicle_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleRun extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "departs_at", nullable = false)
    private LocalDateTime departsAt;

    @Column(columnDefinition = "TEXT")
    private String note;

    private VehicleRun(LocalDateTime departsAt, String note) {
        this.departsAt = departsAt;
        this.note = note;
    }

    public static VehicleRun create(LocalDateTime departsAt, String note) {
        return new VehicleRun(departsAt, note);
    }

    /** null 필드는 미변경(PATCH 부분 수정 — BibleChallenge.applyPatch 관례). */
    public void applyPatch(LocalDateTime departsAt, String note) {
        if (departsAt != null) {
            this.departsAt = departsAt;
        }
        if (note != null) {
            this.note = note;
        }
    }

    /** 출발 시각 도달 = 신청 마감. */
    public boolean isClosed(LocalDateTime now) {
        return !now.isBefore(departsAt);
    }
}
