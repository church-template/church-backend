package com.elipair.church.domain.vehicle;

import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.vehicle.dto.MyRequestResponse;
import com.elipair.church.domain.vehicle.dto.VehicleRequestCreateRequest;
import com.elipair.church.domain.vehicle.dto.VehicleRequestResponse;
import com.elipair.church.domain.vehicle.dto.VehicleRosterEntryResponse;
import com.elipair.church.domain.vehicle.dto.VehicleRunCardResponse;
import com.elipair.church.domain.vehicle.dto.VehicleRunCreateRequest;
import com.elipair.church.domain.vehicle.dto.VehicleRunDetailResponse;
import com.elipair.church.domain.vehicle.dto.VehicleRunPatchRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final VehicleRequestRepository requestRepository;
    private final MemberRepository memberRepository;
    private final AuthorDisplayService authorDisplayService;
    private final Clock clock;

    public VehicleRunService(
            VehicleRunRepository runRepository,
            VehicleRequestRepository requestRepository,
            MemberRepository memberRepository,
            AuthorDisplayService authorDisplayService,
            Clock clock) {
        this.runRepository = runRepository;
        this.requestRepository = requestRepository;
        this.memberRepository = memberRepository;
        this.authorDisplayService = authorDisplayService;
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

    /** 운행일별 통합 명단. 이름은 AuthorDisplayService(탈퇴 마스킹), 연락처는 members에서 일괄 조회(N+1 회피). */
    public Page<VehicleRosterEntryResponse> roster(Long runId, Pageable pageable) {
        load(runId);
        Page<VehicleRequest> requests = requestRepository.findByRunIdAndDeletedAtIsNull(runId, pageable);
        List<Long> memberIds =
                requests.stream().map(VehicleRequest::getMemberId).distinct().toList();
        Map<Long, String> names = authorDisplayService.displayNames(memberIds);
        Map<Long, String> phones = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getPhone));
        return requests.map(request -> new VehicleRosterEntryResponse(
                names.get(request.getMemberId()),
                phones.get(request.getMemberId()),
                request.getPickupLocation(),
                request.getNote(),
                request.getCreatedAt()));
    }

    // ---- 회원 ----

    /** 다가오는 운행일 목록 + 내 신청 포함(별도 "내 신청 조회" 엔드포인트 없음 — 스펙 §API). */
    public Page<VehicleRunCardResponse> upcoming(Long memberId, Pageable pageable) {
        Page<VehicleRun> runs =
                runRepository.findByDeletedAtIsNullAndDepartsAtGreaterThanEqual(LocalDateTime.now(clock), pageable);
        List<Long> runIds = runs.stream().map(VehicleRun::getId).toList();
        Map<Long, VehicleRequest> mine = runIds.isEmpty()
                ? Map.of()
                : requestRepository.findByRunIdInAndMemberIdAndDeletedAtIsNull(runIds, memberId).stream()
                        .collect(Collectors.toMap(VehicleRequest::getRunId, Function.identity()));
        return runs.map(run -> new VehicleRunCardResponse(
                run.getId(),
                run.getDepartsAt(),
                run.getNote(),
                mine.containsKey(run.getId()) ? MyRequestResponse.from(mine.get(run.getId())) : null));
    }

    @Transactional
    public VehicleRequestResponse apply(Long runId, Long memberId, VehicleRequestCreateRequest req) {
        VehicleRun run = load(runId);
        if (run.isClosed(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미 출발한 운행일에는 신청할 수 없습니다");
        }
        if (requestRepository.existsByRunIdAndMemberIdAndDeletedAtIsNull(runId, memberId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 신청한 운행일입니다");
        }
        return VehicleRequestResponse.from(
                requestRepository.save(VehicleRequest.create(runId, memberId, req.pickupLocation(), req.note())));
    }

    /** 본인 신청 취소(소유권 암묵 — 본인 것만 찾아 지운다). 삭제된 운행일이어도 취소는 허용. */
    @Transactional
    public void cancel(Long runId, Long memberId) {
        requestRepository
                .findByRunIdAndMemberIdAndDeletedAtIsNull(runId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND))
                .softDelete();
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
