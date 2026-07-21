package com.elipair.church.domain.vehicle.dto;

import java.time.LocalDateTime;

public record VehicleRunCardResponse(Long id, LocalDateTime departsAt, String note, MyRequestResponse myRequest) {}
