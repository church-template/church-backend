package com.elipair.church.domain.vehicle;

import com.elipair.church.domain.vehicle.dto.VehicleRunCreateRequest;
import com.elipair.church.domain.vehicle.dto.VehicleRunDetailResponse;
import com.elipair.church.domain.vehicle.dto.VehicleRunPatchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 차량운행 관리 API(이슈 #62). 전 메서드 VEHICLE_MANAGE — 명단에 연락처(개인정보)가 담기므로 조회부터 권한이 필요하다. */
@Tag(name = "차량운행")
@RestController
@PreAuthorize("hasAuthority('VEHICLE_MANAGE')")
public class AdminVehicleRunController {

    private final VehicleRunService service;

    public AdminVehicleRunController(VehicleRunService service) {
        this.service = service;
    }

    @Operation(summary = "운행일 등록", description = """
                    새 운행일을 등록한다(201 Created).

                    - 인증(JWT): 필요 — `VEHICLE_MANAGE`
                    - 요청 본문: `VehicleRunCreateRequest` — `departsAt`(운행 일시, 필수·신청 마감 시각 겸함)·`note`(선택)
                    - 반환값: `VehicleRunDetailResponse` — `version` 포함
                    """)
    @PostMapping("/api/admin/vehicle-runs")
    public ResponseEntity<VehicleRunDetailResponse> create(@Valid @RequestBody VehicleRunCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "운행일 수정", description = """
                    운행일을 부분 수정한다(PATCH). null 필드는 미변경.

                    - 인증(JWT): 필요 — `VEHICLE_MANAGE`
                    - 경로 변수: `id` — 수정할 운행일 ID
                    - 요청 본문: `VehicleRunPatchRequest` — 변경 필드 + `version`(낙관락, 필수)
                    - 반환값: `VehicleRunDetailResponse`(`version`은 증가 후 값)
                    - 부수효과: `version` 불일치 시 409
                    """)
    @PatchMapping("/api/admin/vehicle-runs/{id}")
    public VehicleRunDetailResponse patch(@PathVariable Long id, @Valid @RequestBody VehicleRunPatchRequest request) {
        return service.patch(id, request);
    }

    @Operation(summary = "운행일 삭제", description = """
                    운행일을 삭제한다(204 No Content). "이번 주 운행 없음"도 이 API로 처리한다.

                    - 인증(JWT): 필요 — `VEHICLE_MANAGE`
                    - 경로 변수: `id` — 삭제할 운행일 ID
                    - 반환값: 없음(204)
                    - 부수효과: soft delete — 신청 행은 이력 보존, 목록·명단에서만 제외
                    """)
    @DeleteMapping("/api/admin/vehicle-runs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @Operation(summary = "운행일 전체 목록", description = """
                    운행일 전체 목록을 조회한다(지난 운행 포함, 최신 출발순).

                    - 인증(JWT): 필요 — `VEHICLE_MANAGE`
                    - 요청 파라미터: `page`·`size`·`sort` — 페이지네이션(기본 `departsAt,desc`)
                    - 반환값: `Page<VehicleRunDetailResponse>`
                    """)
    @GetMapping("/api/admin/vehicle-runs")
    public Page<VehicleRunDetailResponse> list(
            @PageableDefault(size = 10, sort = "departsAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.adminList(pageable);
    }
}
