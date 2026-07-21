package com.elipair.church.domain.vehicle;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRunRepository extends JpaRepository<VehicleRun, Long> {

    Optional<VehicleRun> findByIdAndDeletedAtIsNull(Long id);

    /** 관리자 전체 목록(지난 운행 포함). */
    Page<VehicleRun> findByDeletedAtIsNull(Pageable pageable);

    /** 회원 목록 — 다가오는 운행일만. */
    Page<VehicleRun> findByDeletedAtIsNullAndDepartsAtGreaterThanEqual(LocalDateTime now, Pageable pageable);
}
