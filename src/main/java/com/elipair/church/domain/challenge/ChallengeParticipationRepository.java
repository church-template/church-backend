package com.elipair.church.domain.challenge;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeParticipationRepository extends JpaRepository<ChallengeParticipation, Long> {

    Optional<ChallengeParticipation> findByChallengeIdAndMemberIdAndDeletedAtIsNull(Long challengeId, Long memberId);

    boolean existsByChallengeIdAndMemberIdAndDeletedAtIsNull(Long challengeId, Long memberId);

    /** 관리자 구간·기간 수정 가드(설계 §3): 참여자가 하나라도 있으면 구조 필드 수정 거부. */
    boolean existsByChallengeIdAndDeletedAtIsNull(Long challengeId);

    Page<ChallengeParticipation> findByMemberIdAndDeletedAtIsNull(Long memberId, Pageable pageable);
}
