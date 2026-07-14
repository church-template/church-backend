package com.elipair.church.domain.inquiry;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    Optional<Inquiry> findByIdAndDeletedAtIsNull(Long id);

    Page<Inquiry> findByDeletedAtIsNull(Pageable pageable);

    Page<Inquiry> findByDeletedAtIsNullAndCompletedAtIsNull(Pageable pageable);

    Page<Inquiry> findByDeletedAtIsNullAndCompletedAtIsNotNull(Pageable pageable);
}
