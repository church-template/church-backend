package com.elipair.church.domain.vehicle.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * 탑승 신청 요청(이슈 #65). pickupLocation은 선택 — 픽업 텍스트·좌표 중 최소 하나는 필수.
 * 좌표는 쌍으로만 유효(한쪽만 오면 거부), 위도 -90~90·경도 -180~180.
 * Double은 @DecimalMin/@Max가 미지원이라 범위·교차검증을 @AssertTrue로 둔다(EventCreateRequest 선례).
 */
public record VehicleRequestCreateRequest(
        @Size(max = 200) String pickupLocation, String note, Double latitude, Double longitude) {

    /** 공백만인 픽업 텍스트는 미존재로 취급(저장 정규화도 서비스가 동일 기준). */
    public boolean hasPickup() {
        return pickupLocation != null && !pickupLocation.isBlank();
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    @AssertTrue(message = "위도와 경도는 함께 입력해야 합니다")
    public boolean isCoordinatesPaired() {
        return (latitude == null) == (longitude == null);
    }

    @AssertTrue(message = "픽업 장소 또는 좌표 중 최소 하나를 입력해야 합니다")
    public boolean isPickupOrCoordinatesPresent() {
        return hasPickup() || hasCoordinates();
    }

    @AssertTrue(message = "좌표 범위가 올바르지 않습니다(위도 -90~90, 경도 -180~180)")
    public boolean isCoordinatesInRange() {
        return (latitude == null || (latitude >= -90 && latitude <= 90))
                && (longitude == null || (longitude >= -180 && longitude <= 180));
    }
}
