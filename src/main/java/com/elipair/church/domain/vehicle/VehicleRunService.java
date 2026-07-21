package com.elipair.church.domain.vehicle;

import com.elipair.church.domain.vehicle.dto.VehicleRunCreateRequest;
import com.elipair.church.domain.vehicle.dto.VehicleRunDetailResponse;
import com.elipair.church.domain.vehicle.dto.VehicleRunPatchRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차량운행(이슈 #62, 설계 2026-07-21). 운행일 CRUD·명단은 VEHICLE_MANAGE, 목록·신청·취소는 VEHICLE_APPLY.
 * 낙관락은 명시 version 비교 + flush(Notice/Challenge 선례).
 */
@Service
@Transactional(readOnly = true)
public class VehicleRunService {

    private final VehicleRunRepository runRepository;
    private final Clock clock;

    public VehicleRunService(VehicleRunRepository runRepository, Clock clock) {
        this.runRepository = runRepository;
        this.clock = clock;
    }

    // ---- 관리자 ----

    @Transactional
    public VehicleRunDetailResponse create(VehicleRunCreateRequest req) {
        return VehicleRunDetailResponse.from(runRepository.save(VehicleRun.create(req.departsAt(), req.note())));
    }

    @Transactional
    public VehicleRunDetailResponse patch(Long id, VehicleRunPatchRequest req) {
        VehicleRun run = load(id);
        checkVersion(run, req.version());
        run.applyPatch(req.departsAt(), req.note());
        runRepository.flush(); // 응답 version post-increment (Notice/Challenge 선례)
        return VehicleRunDetailResponse.from(run);
    }

    @Transactional
    public void delete(Long id) {
        load(id).softDelete();
        // 신청 행은 이력 보존을 위해 그대로 둔다 — 삭제된 운행일은 목록·명단 조회에서 자연 제외된다.
    }

    public Page<VehicleRunDetailResponse> adminList(Pageable pageable) {
        return runRepository.findByDeletedAtIsNull(pageable).map(VehicleRunDetailResponse::from);
    }

    private VehicleRun load(Long id) {
        return runRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** null-safe — 미영속 엔티티(version null)는 0으로 비교(BibleChallengeService 선례). */
    private void checkVersion(VehicleRun run, Long expected) {
        Long current = run.getVersion() == null ? 0L : run.getVersion();
        if (!current.equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }
}
