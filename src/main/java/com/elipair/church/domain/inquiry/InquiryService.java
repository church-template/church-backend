package com.elipair.church.domain.inquiry;

import com.elipair.church.domain.inquiry.dto.InquiryCardResponse;
import com.elipair.church.domain.inquiry.dto.InquiryCreateRequest;
import com.elipair.church.domain.inquiry.dto.InquiryCreatedResponse;
import com.elipair.church.domain.inquiry.dto.InquiryDetailResponse;
import com.elipair.church.domain.member.PhoneNumbers;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문의 서비스(이슈 #50). 등록은 공개, 조회·완료·삭제는 INQUIRY_MANAGE.
 * 등록 내용은 수정 API가 없어 낙관락 version을 노출하지 않는다 — 관리자가 바꾸는 건 완료 플래그뿐이고,
 * 완료 토글은 멱등이라 동시 클릭이 충돌을 만들지 않는다(JPA @Version은 백스톱으로 그대로 유지).
 */
@Service
@Transactional(readOnly = true)
public class InquiryService {

    private final InquiryRepository repository;
    private final InquiryRateLimiter rateLimiter;
    private final Clock clock;

    public InquiryService(InquiryRepository repository, InquiryRateLimiter rateLimiter, Clock clock) {
        this.repository = repository;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    public InquiryCreatedResponse create(InquiryCreateRequest req, String clientIp) {
        rateLimiter.check(clientIp);
        LocalDateTime now = LocalDateTime.now(clock);
        Inquiry inquiry = repository.save(
                Inquiry.create(req.name(), PhoneNumbers.normalize(req.phone()), req.email(), req.content(), now));
        return new InquiryCreatedResponse(inquiry.getId());
    }

    /** completed=null이면 전체, false면 미처리, true면 처리 완료만. */
    public Page<InquiryCardResponse> list(Boolean completed, Pageable pageable) {
        Page<Inquiry> page;
        if (completed == null) {
            page = repository.findByDeletedAtIsNull(pageable);
        } else if (completed) {
            page = repository.findByDeletedAtIsNullAndCompletedAtIsNotNull(pageable);
        } else {
            page = repository.findByDeletedAtIsNullAndCompletedAtIsNull(pageable);
        }
        return page.map(i -> new InquiryCardResponse(
                i.getId(),
                i.getName(),
                i.getPhone(),
                i.getEmail(),
                i.isCompleted(),
                i.getCompletedAt(),
                i.getCreatedAt()));
    }

    public InquiryDetailResponse get(Long id) {
        return detail(load(id));
    }

    @Transactional
    public InquiryDetailResponse complete(Long id, boolean completed) {
        Inquiry inquiry = load(id);
        inquiry.markCompleted(completed, LocalDateTime.now(clock));
        return detail(inquiry);
    }

    @Transactional
    public void delete(Long id) {
        load(id).softDelete();
    }

    private Inquiry load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private InquiryDetailResponse detail(Inquiry i) {
        return new InquiryDetailResponse(
                i.getId(),
                i.getName(),
                i.getPhone(),
                i.getEmail(),
                i.getContent(),
                i.isCompleted(),
                i.getCompletedAt(),
                i.getCreatedAt());
    }
}
