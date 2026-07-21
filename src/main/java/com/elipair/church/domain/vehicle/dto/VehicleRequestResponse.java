package com.elipair.church.domain.vehicle.dto;

import com.elipair.church.domain.vehicle.VehicleRequest;

public record VehicleRequestResponse(Long id, Long runId, String pickupLocation, String note) {

    public static VehicleRequestResponse from(VehicleRequest request) {
        return new VehicleRequestResponse(
                request.getId(), request.getRunId(), request.getPickupLocation(), request.getNote());
    }
}
