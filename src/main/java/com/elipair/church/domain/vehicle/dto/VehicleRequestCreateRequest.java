package com.elipair.church.domain.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleRequestCreateRequest(
        @NotBlank @Size(max = 200) String pickupLocation, String note) {}
