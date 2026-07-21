package com.elipair.church.domain.vehicle.dto;

import java.time.LocalDateTime;

/** 운행일별 통합 명단 한 줄 — 이름·연락처는 members에서 조회(탈퇴 시 이름 마스킹), 신청순 정렬. */
public record VehicleRosterEntryResponse(
        String name, String phone, String pickupLocation, String note, LocalDateTime requestedAt) {}
