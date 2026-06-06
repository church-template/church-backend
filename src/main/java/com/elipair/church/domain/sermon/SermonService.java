package com.elipair.church.domain.sermon;

import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.domain.sermon.dto.SermonCardResponse;
import com.elipair.church.domain.sermon.dto.SermonCreateRequest;
import com.elipair.church.domain.sermon.dto.SermonDetailResponse;
import com.elipair.church.domain.sermon.dto.SermonPatchRequest;
import com.elipair.church.domain.sermon.dto.SermonUpdateRequest;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설교 서비스(스펙 §5.5). 태그(ContentTagService)·작성자(AuthorDisplayService)와 조립한다.
 * 조회수는 incrementViewCount(원자 UPDATE, @Version 우회). 낙관락은 명시적 version 비교(백스톱은 JPA @Version).
 */
@Service
@Transactional(readOnly = true)
public class SermonService {

    private static final ContentResourceType TYPE = ContentResourceType.SERMON;

    private final SermonRepository repository;
    private final ContentTagService contentTagService;
    private final AuthorDisplayService authorDisplayService;

    public SermonService(
            SermonRepository repository,
            ContentTagService contentTagService,
            AuthorDisplayService authorDisplayService) {
        this.repository = repository;
        this.contentTagService = contentTagService;
        this.authorDisplayService = authorDisplayService;
    }

    public Page<SermonCardResponse> list(
            String preacher, String series, LocalDate from, LocalDate to, String q, Long tagId, Pageable pageable) {
        List<Long> taggedIds = tagId == null ? null : contentTagService.resourceIdsWithTag(TYPE, tagId);
        Page<Sermon> page =
                repository.findAll(SermonSpecifications.filter(preacher, series, from, to, q, taggedIds), pageable);

        List<Long> ids = page.map(Sermon::getId).getContent();
        Map<Long, List<TagResponse>> tagsMap = contentTagService.getTagsByResources(TYPE, ids);
        Map<Long, String> authorMap =
                authorDisplayService.displayNames(page.map(Sermon::getUpdatedBy).getContent());

        return page.map(s -> new SermonCardResponse(
                s.getId(),
                s.getTitle(),
                s.getPreacher(),
                s.getSeries(),
                s.getScripture(),
                s.getPreachedAt(),
                s.getViewCount(),
                tagsMap.getOrDefault(s.getId(), List.of()),
                authorMap.getOrDefault(s.getUpdatedBy(), AuthorDisplayService.UNKNOWN)));
    }

    @Transactional
    public SermonDetailResponse get(Long id) {
        repository.incrementViewCount(id); // 먼저 증가(clearAutomatically) → 아래 재조회가 +1 반영본을 읽음
        Sermon sermon = repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return detail(sermon);
    }

    @Transactional
    public SermonDetailResponse create(SermonCreateRequest req) {
        Sermon sermon = repository.save(Sermon.create(
                req.title(),
                req.preacher(),
                req.series(),
                req.scripture(),
                req.content(),
                req.videoUrl(),
                req.audioUrl(),
                req.preachedAt()));
        contentTagService.replaceLinks(TYPE, sermon.getId(), req.tagIds());
        return detail(sermon);
    }

    @Transactional
    public SermonDetailResponse update(Long id, SermonUpdateRequest req) {
        Sermon sermon = load(id);
        checkVersion(sermon, req.version());
        sermon.update(
                req.title(),
                req.preacher(),
                req.series(),
                req.scripture(),
                req.content(),
                req.videoUrl(),
                req.audioUrl(),
                req.preachedAt());
        contentTagService.replaceLinks(TYPE, id, req.tagIds());
        return detail(sermon);
    }

    @Transactional
    public SermonDetailResponse patch(Long id, SermonPatchRequest req) {
        Sermon sermon = load(id);
        checkVersion(sermon, req.version());
        sermon.applyPatch(
                req.title(),
                req.preacher(),
                req.series(),
                req.scripture(),
                req.content(),
                req.videoUrl(),
                req.audioUrl(),
                req.preachedAt());
        if (req.tagIds() != null) {
            contentTagService.replaceLinks(TYPE, id, req.tagIds());
        }
        return detail(sermon);
    }

    @Transactional
    public void delete(Long id) {
        Sermon sermon = load(id);
        sermon.softDelete();
        contentTagService.cleanUp(TYPE, id);
    }

    private Sermon load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void checkVersion(Sermon sermon, Long expected) {
        if (!sermon.getVersion().equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private SermonDetailResponse detail(Sermon s) {
        return new SermonDetailResponse(
                s.getId(),
                s.getTitle(),
                s.getPreacher(),
                s.getSeries(),
                s.getScripture(),
                s.getContent(),
                s.getVideoUrl(),
                s.getAudioUrl(),
                s.getPreachedAt(),
                s.getViewCount(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getVersion(),
                contentTagService.getTags(TYPE, s.getId()),
                authorDisplayService.displayName(s.getUpdatedBy()));
    }
}
