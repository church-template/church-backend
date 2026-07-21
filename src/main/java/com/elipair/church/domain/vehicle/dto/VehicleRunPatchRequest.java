package com.elipair.church.domain.vehicle.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** null 필드는 미변경. version은 낙관락 필수(스펙 §낙관적 잠금). */
public record VehicleRunPatchRequest(
        LocalDateTime departsAt, String note, @NotNull Long version) {}
