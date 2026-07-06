package com.elipair.church.domain.challenge;

import com.elipair.church.domain.challenge.dto.ChallengeCardResponse;
import com.elipair.church.domain.challenge.dto.ChallengeCreateRequest;
import com.elipair.church.domain.challenge.dto.ChallengeDetailResponse;
import com.elipair.church.domain.challenge.dto.ChallengePatchRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챌린지 관리 CRUD + 목록/상세(설계 §3). 진행 기록은 ChallengeProgressService 담당.
 * 참여자가 있는 챌린지의 구간·기간 수정은 진행률 의미를 깨므로 거부(400). 낙관락은 명시 version 비교 + flush(Notice 선례).
 */
@Service
@Transactional(readOnly = true)
public class BibleChallengeService {

    private final BibleChallengeRepository repository;
    private final ChallengeParticipationRepository participationRepository;
    private final Clock clock;

    public BibleChallengeService(
            BibleChallengeRepository repository,
            ChallengeParticipationRepository participationRepository,
            Clock clock) {
        this.repository = repository;
        this.participationRepository = participationRepository;
        this.clock = clock;
    }

    public Page<ChallengeCardResponse> list(Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        return repository.findAllByDeletedAtIsNull(pageable).map(c -> ChallengeCardResponse.from(c, today));
    }

    public ChallengeDetailResponse get(Long id, Long memberId) {
        BibleChallenge challenge = load(id);
        boolean joined = participationRepository.existsByChallengeIdAndMemberIdAndDeletedAtIsNull(id, memberId);
        return ChallengeDetailResponse.from(challenge, LocalDate.now(clock), joined);
    }

    @Transactional
    public ChallengeDetailResponse create(ChallengeCreateRequest req) {
        validateBookRange(req.startBook(), req.endBook());
        BibleChallenge challenge = repository.save(BibleChallenge.create(
                req.title(), req.description(), req.startBook(), req.endBook(), req.startDate(), req.targetDays()));
        return ChallengeDetailResponse.from(challenge, LocalDate.now(clock), false);
    }

    @Transactional
    public ChallengeDetailResponse patch(Long id, ChallengePatchRequest req) {
        BibleChallenge challenge = load(id);
        checkVersion(challenge, req.version());
        boolean structureChange =
                req.startBook() != null || req.endBook() != null || req.startDate() != null || req.targetDays() != null;
        if (structureChange && participationRepository.existsByChallengeIdAndDeletedAtIsNull(id)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "참여자가 있는 챌린지의 구간·기간은 수정할 수 없습니다");
        }
        if (structureChange) {
            int newStart = req.startBook() != null ? req.startBook() : challenge.getStartBook();
            int newEnd = req.endBook() != null ? req.endBook() : challenge.getEndBook();
            validateBookRange(newStart, newEnd);
        }
        challenge.applyPatch(
                req.title(), req.description(), req.startBook(), req.endBook(), req.startDate(), req.targetDays());
        repository.flush(); // 응답 version post-increment (Notice/Sermon 선례)
        return ChallengeDetailResponse.from(challenge, LocalDate.now(clock), false);
    }

    @Transactional
    public void delete(Long id) {
        load(id).softDelete();
        // 참여·로그는 이력 보존을 위해 그대로 둔다 — 마이페이지 이력에는 남고 신규 기록은 404(설계 §3 delete).
    }

    private BibleChallenge load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** null-safe — 미영속 엔티티(version null)는 0으로 비교(단위 테스트가 새 엔티티를 그대로 쓴다). */
    private void checkVersion(BibleChallenge challenge, Long expected) {
        Long current = challenge.getVersion() == null ? 0L : challenge.getVersion();
        if (!current.equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private void validateBookRange(int startBook, int endBook) {
        if (startBook > endBook) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "startBook은 endBook보다 클 수 없습니다");
        }
    }
}
