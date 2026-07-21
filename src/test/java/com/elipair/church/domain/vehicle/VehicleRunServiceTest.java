package com.elipair.church.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.vehicle.dto.VehicleRequestCreateRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 동시 중복 신청 레이스의 409 매핑 검증(리뷰 Important 후속). exists 선검사를 두 요청이 동시에 통과하면
 * 부분 유니크(uq_vehicle_requests_active)가 백스톱으로 막는데, 이때 500이 아니라 409여야 한다.
 * 통합 테스트로는 레이스를 결정적으로 재현할 수 없어 단위로 고정한다.
 */
class VehicleRunServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void apply_maps_concurrent_unique_violation_to_duplicate_409() {
        VehicleRunRepository runRepository = mock(VehicleRunRepository.class);
        VehicleRequestRepository requestRepository = mock(VehicleRequestRepository.class);
        VehicleRunService service = new VehicleRunService(
                runRepository,
                requestRepository,
                mock(MemberRepository.class),
                mock(AuthorDisplayService.class),
                FIXED);
        VehicleRun run = VehicleRun.create(LocalDateTime.now(FIXED).plusDays(1), null);
        when(runRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(run));
        when(requestRepository.existsByRunIdAndMemberIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(false);
        when(requestRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uq_vehicle_requests_active"));

        assertThatThrownBy(() -> service.apply(1L, 2L, new VehicleRequestCreateRequest("OO아파트 정문", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
    }
}
