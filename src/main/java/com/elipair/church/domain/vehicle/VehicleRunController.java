package com.elipair.church.domain.vehicle;

import com.elipair.church.domain.vehicle.dto.VehicleRequestCreateRequest;
import com.elipair.church.domain.vehicle.dto.VehicleRequestResponse;
import com.elipair.church.domain.vehicle.dto.VehicleRunCardResponse;
import com.elipair.church.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 차량운행 회원 API(이슈 #62). 경로 전체가 VEHICLE_APPLY(승인 교인) — SecurityConfig 매처가 게이트. */
@Tag(name = "차량운행")
@RestController
public class VehicleRunController {

    private final VehicleRunService service;

    public VehicleRunController(VehicleRunService service) {
        this.service = service;
    }

    @Operation(summary = "다가오는 운행일 목록", description = """
                    다가오는 운행일 목록을 조회한다(출발 임박순). 각 항목에 내 신청(`myRequest`)이 포함된다 — null이면 미신청.

                    - 인증(JWT): 필요 — `VEHICLE_APPLY`(승인 교인)
                    - 요청 파라미터: `page`·`size`·`sort` — 페이지네이션(기본 `departsAt,asc`)
                    - 반환값: `Page<VehicleRunCardResponse>` — `id`·`departsAt`·`note`·`myRequest{pickupLocation, note, latitude, longitude}`
                    """)
    @GetMapping("/api/vehicle-runs")
    public Page<VehicleRunCardResponse> list(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PageableDefault(size = 10, sort = "departsAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.upcoming(principal.id(), pageable);
    }

    @Operation(summary = "탑승 신청", description = """
                    운행일에 탑승을 신청한다(201 Created).

                    - 인증(JWT): 필요 — `VEHICLE_APPLY`(승인 교인)
                    - 경로 변수: `id` — 운행일 ID
                    - 요청 본문: `VehicleRequestCreateRequest` — `pickupLocation`(선택, 최대 200자)·`note`(선택)·`latitude`/`longitude`(선택, 위경도 좌표). 픽업 텍스트와 좌표 중 최소 하나는 필수
                    - 반환값: `VehicleRequestResponse` — 저장된 픽업 텍스트·좌표 포함
                    - 부수효과: 중복 신청 409(`DUPLICATE_RESOURCE`) · 출발 시각 경과 400 · 없는/삭제된 운행일 404 · 픽업·좌표 모두 없음/좌표 한쪽만/범위 초과 400(`INVALID_INPUT_VALUE`)
                    """)
    @PostMapping("/api/vehicle-runs/{id}/requests")
    public ResponseEntity<VehicleRequestResponse> apply(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody VehicleRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.apply(id, principal.id(), request));
    }

    @Operation(summary = "탑승 신청 취소", description = """
                    해당 운행일의 내 신청을 취소한다(204 No Content).

                    - 인증(JWT): 필요 — `VEHICLE_APPLY`(승인 교인)
                    - 경로 변수: `id` — 운행일 ID
                    - 반환값: 없음(204) — 활성 신청이 없으면 404
                    - 부수효과: soft delete — 취소 후 재신청 가능(부분 유니크)
                    """)
    @DeleteMapping("/api/vehicle-runs/{id}/requests/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        service.cancel(id, principal.id());
    }
}
