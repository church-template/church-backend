package com.elipair.church.domain.vehicle;

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
 * 탑승 신청(이슈 #62). member는 BaseEntity 관례대로 Long id 참조(연관 아님).
 * 취소 = 소프트삭제, (run_id, member_id) 부분 유니크로 재신청 허용(challenge_participations 관례).
 */
@Entity
@Table(name = "vehicle_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "pickup_location", nullable = false, length = 200)
    private String pickupLocation;

    @Column(columnDefinition = "TEXT")
    private String note;

    private VehicleRequest(Long runId, Long memberId, String pickupLocation, String note) {
        this.runId = runId;
        this.memberId = memberId;
        this.pickupLocation = pickupLocation;
        this.note = note;
    }

    public static VehicleRequest create(Long runId, Long memberId, String pickupLocation, String note) {
        return new VehicleRequest(runId, memberId, pickupLocation, note);
    }
}
