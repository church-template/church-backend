package com.elipair.church.domain.media;

import com.elipair.church.domain.media.dto.MediaReferencesResponse;
import com.elipair.church.domain.media.dto.MediaResponse;
import com.elipair.church.global.common.ContentRef;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.exception.MediaInUseException;
import com.elipair.church.global.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 중앙 미디어 라이브러리 서비스(스펙 §5.10). 업로드는 매직바이트로 형식을 확정해 저장하고,
 * 삭제는 참조(MediaReferenceProvider 합집합)가 하나라도 있으면 차단한다.
 */
@Service
@Transactional(readOnly = true)
public class MediaService {

    /** 매직바이트 판별에 충분한 헤더 길이(WEBP가 12바이트로 가장 길다). */
    private static final int HEADER_BYTES = 12;

    private final MediaRepository repository;
    private final FileStorage fileStorage;
    private final MimeTypeValidator mimeTypeValidator;
    private final List<MediaReferenceProvider> referenceProviders;

    public MediaService(
            MediaRepository repository,
            FileStorage fileStorage,
            MimeTypeValidator mimeTypeValidator,
            List<MediaReferenceProvider> referenceProviders) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.mimeTypeValidator = mimeTypeValidator;
        this.referenceProviders = referenceProviders;
    }

    @Transactional
    public MediaResponse upload(MultipartFile file, Long uploaderId) {
        return persist(file, detectMime(file), uploaderId);
    }

    /** 갤러리 사진 전용 — 저장 전에 이미지 여부를 확정해 비이미지는 파일을 쓰지 않고 거부(고아 파일 차단, 설계 §7). */
    @Transactional
    public MediaResponse uploadImage(MultipartFile file, Long uploaderId) {
        String mimeType = detectMime(file);
        if (!mimeType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미지 파일만 업로드할 수 있습니다");
        }
        return persist(file, mimeType, uploaderId);
    }

    /** 주보 전용 — 저장 전에 PDF 여부를 확정해 비PDF는 파일을 쓰지 않고 거부(고아 파일 차단, 설계 §6.1). */
    @Transactional
    public MediaResponse uploadPdf(MultipartFile file, Long uploaderId) {
        String mimeType = detectMime(file);
        if (!mimeType.equals("application/pdf")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "PDF 파일만 업로드할 수 있습니다");
        }
        return persist(file, mimeType, uploaderId);
    }

    /** 기존 라이브러리에서 고른 mediaId가 존재하고 PDF인지 검증(설계 §6.1). */
    public void requirePdf(Long mediaId) {
        if (mediaId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "mediaId는 필수입니다");
        }
        Media media = findById(mediaId); // 미존재 시 RESOURCE_NOT_FOUND
        if (!media.getMimeType().equals("application/pdf")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "PDF 미디어만 연결할 수 있습니다");
        }
    }

    /** 기존 라이브러리에서 고른 mediaIds가 모두 존재하고 이미지인지 검증(설계 §7). 빈 입력은 무검증 통과. */
    public void requireImages(Collection<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        if (mediaIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "mediaIds에는 null을 포함할 수 없습니다");
        }
        List<Long> distinct = mediaIds.stream().distinct().toList();
        List<Media> found = repository.findAllById(distinct);
        if (found.size() != distinct.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        for (Media media : found) {
            if (!media.getMimeType().startsWith("image/")) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미지 미디어만 추가할 수 있습니다");
            }
        }
    }

    private MediaResponse persist(MultipartFile file, String mimeType, Long uploaderId) {
        String storedPath = fileStorage.store(file);
        try {
            String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload";
            Media media = repository.save(Media.create(filename, storedPath, mimeType, file.getSize(), uploaderId));
            return MediaResponse.from(media);
        } catch (RuntimeException e) {
            // DB 저장 실패 시 방금 쓴 파일을 best-effort 정리 — 레코드 없는 고아 파일은 차단삭제로도 못 지우는 진짜 누수.
            try {
                fileStorage.delete(storedPath);
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    public Page<MediaResponse> list(String type, LocalDate from, LocalDate to, Pageable pageable) {
        String mimePrefix = mimePrefix(type);
        LocalDateTime fromAt = from == null ? null : from.atStartOfDay();
        LocalDateTime toAt = to == null ? null : to.plusDays(1).atStartOfDay(); // 상한 배타(해당 날짜 포함)
        return repository.search(mimePrefix, fromAt, toAt, pageable).map(MediaResponse::from);
    }

    public MediaResponse get(Long id) {
        return MediaResponse.from(findById(id));
    }

    /** 공개 서빙용. stored_path를 외부에 노출하지 않고 로드된 Resource만 전달한다. */
    public MediaContent serve(Long id) {
        Media media = findById(id);
        Resource resource = fileStorage.load(media.getStoredPath());
        return new MediaContent(resource, media.getMimeType(), media.getFilename());
    }

    public MediaReferencesResponse references(Long id) {
        findById(id); // 미존재면 404
        List<ContentRef> refs = collectReferences(id);
        return new MediaReferencesResponse(id, !refs.isEmpty(), refs);
    }

    @Transactional
    public void delete(Long id) {
        Media media = findById(id);
        List<ContentRef> refs = collectReferences(id);
        if (!refs.isEmpty()) {
            throw new MediaInUseException(refs); // 차단형: 본문·갤러리·주보가 쓰면 삭제 거부
        }
        repository.delete(media);
        repository.flush(); // DB 행 먼저 확정
        fileStorage.delete(media.getStoredPath()); // 파일 나중: I/O 실패 시 tx 롤백 → 미디어 온전(깨진 참조 0)
    }

    private Media findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private List<ContentRef> collectReferences(long id) {
        return referenceProviders.stream()
                .flatMap(provider -> provider.findReferences(id).stream())
                .toList();
    }

    private String detectMime(MultipartFile file) {
        return mimeTypeValidator
                .detect(readHeader(file))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 파일 형식입니다 (이미지 또는 PDF만 업로드할 수 있습니다)"));
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEADER_BYTES);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    private String mimePrefix(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "image" -> "image/%";
            case "pdf" -> "application/pdf";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "type은 image 또는 pdf만 허용됩니다");
        };
    }
}
