package com.elipair.church.domain.vehicle.dto;

import com.elipair.church.domain.vehicle.VehicleRequest;

/** 회원 운행일 카드의 "내 신청" — null이면 미신청. */
public record MyRequestResponse(String pickupLocation, String note, Double latitude, Double longitude) {

    public static MyRequestResponse from(VehicleRequest request) {
        return new MyRequestResponse(
                request.getPickupLocation(), request.getNote(), request.getLatitude(), request.getLongitude());
    }
}
