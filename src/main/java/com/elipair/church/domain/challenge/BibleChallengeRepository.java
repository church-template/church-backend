package com.elipair.church.domain.challenge;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BibleChallengeRepository extends JpaRepository<BibleChallenge, Long> {

    Optional<BibleChallenge> findByIdAndDeletedAtIsNull(Long id);

    Page<BibleChallenge> findAllByDeletedAtIsNull(Pageable pageable);
}
