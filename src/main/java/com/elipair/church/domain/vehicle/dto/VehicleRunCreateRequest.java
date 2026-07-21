package com.elipair.church.domain.vehicle.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record VehicleRunCreateRequest(@NotNull LocalDateTime departsAt, String note) {}
