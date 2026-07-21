package com.elipair.church.domain.vehicle;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRequestRepository extends JpaRepository<VehicleRequest, Long> {

    boolean existsByRunIdAndMemberIdAndDeletedAtIsNull(Long runId, Long memberId);

    Optional<VehicleRequest> findByRunIdAndMemberIdAndDeletedAtIsNull(Long runId, Long memberId);

    /** 회원 목록의 "내 신청" 일괄 조회(N+1 회피). */
    List<VehicleRequest> findByRunIdInAndMemberIdAndDeletedAtIsNull(Collection<Long> runIds, Long memberId);

    /** 운행일별 명단(신청순). */
    Page<VehicleRequest> findByRunIdAndDeletedAtIsNull(Long runId, Pageable pageable);
}
