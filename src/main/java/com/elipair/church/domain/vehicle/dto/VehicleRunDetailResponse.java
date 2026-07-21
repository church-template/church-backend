package com.elipair.church.domain.vehicle.dto;

import com.elipair.church.domain.vehicle.VehicleRun;
import java.time.LocalDateTime;

public record VehicleRunDetailResponse(Long id, LocalDateTime departsAt, String note, Long version) {

    public static VehicleRunDetailResponse from(VehicleRun run) {
        return new VehicleRunDetailResponse(run.getId(), run.getDepartsAt(), run.getNote(), run.getVersion());
    }
}
