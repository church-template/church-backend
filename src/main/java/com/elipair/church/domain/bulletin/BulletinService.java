package com.elipair.church.domain.bulletin;

import com.elipair.church.domain.bulletin.dto.BulletinCardResponse;
import com.elipair.church.domain.bulletin.dto.BulletinDetailResponse;
import com.elipair.church.domain.media.MediaService;
import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주보 서비스(스펙 §5.13). notice 답습(공개 CRUD·작성자 표시·낙관락) + 갤러리식 PDF 연결(MediaService).
 * 모든 입력 검증·낙관락 확인은 uploadPdf(디스크 쓰기)보다 먼저 수행한다 — 검증/충돌 실패가 고아 파일을 남기지 않도록(설계 §6.1).
 */
@Service
@Transactional(readOnly = true)
public class BulletinService {

    private static final int TITLE_MAX = 200;

    private final BulletinRepository repository;
    private final MediaService mediaService;
    private final AuthorDisplayService authorDisplayService;

    public BulletinService(
            BulletinRepository repository, MediaService mediaService, AuthorDisplayService authorDisplayService) {
        this.repository = repository;
        this.mediaService = mediaService;
        this.authorDisplayService = authorDisplayService;
    }

    public Page<BulletinCardResponse> list(Pageable pageable) {
        Page<Bulletin> page = repository.findByDeletedAtIsNull(pageable);
        Map<Long, String> authorMap = authorDisplayService.displayNames(
                page.map(Bulletin::getUpdatedBy).getContent());
        return page.map(b -> new BulletinCardResponse(
                b.getId(),
                b.getTitle(),
                b.getServiceDate(),
                b.getMediaId(),
                b.getCreatedAt(),
                authorMap.getOrDefault(b.getUpdatedBy(), AuthorDisplayService.UNKNOWN)));
    }

    public BulletinDetailResponse get(Long id) {
        return detail(load(id));
    }

    @Transactional
    public BulletinDetailResponse create(
            String title, LocalDate serviceDate, MultipartFile file, Long mediaId, Long uploaderId) {
        validateTitle(title, true);
        if (serviceDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "serviceDate는 필수입니다");
        }
        long resolvedMediaId = resolveMedia(file, mediaId, true, uploaderId);
        return detail(repository.save(Bulletin.create(title, serviceDate, resolvedMediaId)));
    }

    @Transactional
    public BulletinDetailResponse patch(
            Long id,
            Long version,
            String title,
            LocalDate serviceDate,
            MultipartFile file,
            Long mediaId,
            Long uploaderId) {
        if (version == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "version은 필수입니다");
        }
        validateTitle(title, false);
        boolean replacePdf = hasFile(file) || mediaId != null;

        Bulletin bulletin = load(id);
        checkVersion(bulletin, version); // 낙관락 확인을 업로드보다 먼저 — 충돌 시 파일 쓰기 없음(설계 §6.1)
        Long resolvedMediaId = replacePdf ? resolveMedia(file, mediaId, false, uploaderId) : null;

        bulletin.applyPatch(title, serviceDate, resolvedMediaId);
        repository.flush(); // 버전 UPDATE 즉시 반영 → 응답 version이 post-increment (notice 패턴)
        return detail(bulletin);
    }

    @Transactional
    public void delete(Long id) {
        load(id).softDelete();
    }

    // --- helpers ---

    /** file XOR mediaId를 검증하고 mediaId를 해소한다. 모든 검증은 uploadPdf 이전(설계 §6.1). */
    private long resolveMedia(MultipartFile file, Long mediaId, boolean required, Long uploaderId) {
        boolean hasFile = hasFile(file);
        boolean hasId = mediaId != null;
        if (hasFile && hasId) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "file과 mediaId는 동시에 보낼 수 없습니다");
        }
        if (!hasFile && !hasId) {
            // required=true(create)에서만 도달. patch는 호출 전 replacePdf로 분기해 이 경로로 안 온다.
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "PDF 파일(file) 또는 mediaId가 필요합니다");
        }
        if (hasFile) {
            return mediaService.uploadPdf(file, uploaderId).id();
        }
        mediaService.requirePdf(mediaId);
        return mediaId;
    }

    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private void validateTitle(String title, boolean required) {
        if (title == null) {
            if (required) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "title은 필수입니다");
            }
            return;
        }
        if (!StringUtils.hasText(title) || title.length() > TITLE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "title은 공백일 수 없고 200자 이하여야 합니다");
        }
    }

    private Bulletin load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void checkVersion(Bulletin bulletin, Long expected) {
        if (!bulletin.getVersion().equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private BulletinDetailResponse detail(Bulletin b) {
        return new BulletinDetailResponse(
                b.getId(),
                b.getTitle(),
                b.getServiceDate(),
                b.getMediaId(),
                authorDisplayService.displayName(b.getUpdatedBy()),
                b.getCreatedAt(),
                b.getUpdatedAt(),
                b.getVersion());
    }
}
