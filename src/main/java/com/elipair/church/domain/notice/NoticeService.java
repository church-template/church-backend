package com.elipair.church.domain.notice;

import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.domain.notice.dto.NoticeCardResponse;
import com.elipair.church.domain.notice.dto.NoticeCreateRequest;
import com.elipair.church.domain.notice.dto.NoticeDetailResponse;
import com.elipair.church.domain.notice.dto.NoticePatchRequest;
import com.elipair.church.domain.notice.dto.NoticeUpdateRequest;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.viewcount.ViewCountStore;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 서비스(스펙 §5.7). 태그(ContentTagService)·작성자(AuthorDisplayService)와 조립.
 * 조회수는 incrementViewCount(원자 UPDATE, @Version 우회). 낙관락은 명시적 version 비교(백스톱 JPA @Version).
 * update/patch는 detail 생성 전 repository.flush()로 버전 증가를 즉시 반영(stale version 응답 방지 — 설계 §5).
 */
@Service
@Transactional(readOnly = true)
public class NoticeService {

    private static final ContentResourceType TYPE = ContentResourceType.NOTICE;

    private final NoticeRepository repository;
    private final ContentTagService contentTagService;
    private final AuthorDisplayService authorDisplayService;
    private final ViewCountStore viewCountStore;

    public NoticeService(
            NoticeRepository repository,
            ContentTagService contentTagService,
            AuthorDisplayService authorDisplayService,
            ViewCountStore viewCountStore) {
        this.repository = repository;
        this.contentTagService = contentTagService;
        this.authorDisplayService = authorDisplayService;
        this.viewCountStore = viewCountStore;
    }

    public Page<NoticeCardResponse> list(String q, Long tagId, Pageable pageable) {
        List<Long> taggedIds = tagId == null ? null : contentTagService.resourceIdsWithTag(TYPE, tagId);
        Page<Notice> page = repository.findAll(NoticeSpecifications.filter(q, taggedIds), pageable);

        List<Long> ids = page.map(Notice::getId).getContent();
        Map<Long, List<TagResponse>> tagsMap = contentTagService.getTagsByResources(TYPE, ids);
        Map<Long, String> authorMap =
                authorDisplayService.displayNames(page.map(Notice::getUpdatedBy).getContent());

        return page.map(n -> new NoticeCardResponse(
                n.getId(),
                n.getTitle(),
                n.isPinned(),
                n.getViewCount(),
                n.getCreatedAt(),
                tagsMap.getOrDefault(n.getId(), List.of()),
                authorMap.getOrDefault(n.getUpdatedBy(), AuthorDisplayService.UNKNOWN)));
    }

    @Transactional(readOnly = true)
    public NoticeDetailResponse get(Long id) {
        Notice notice = repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        long buffered = viewCountStore.increment(NoticeViewCountFlushTarget.NAMESPACE, id);
        return detail(notice, notice.getViewCount() + buffered);
    }

    @CacheEvict(value = "main", allEntries = true)
    @Transactional
    public NoticeDetailResponse create(NoticeCreateRequest req) {
        Notice notice = repository.save(Notice.create(req.title(), req.content(), Boolean.TRUE.equals(req.isPinned())));
        contentTagService.replaceLinks(TYPE, notice.getId(), req.tagIds());
        return detail(notice);
    }

    @CacheEvict(value = "main", allEntries = true)
    @Transactional
    public NoticeDetailResponse update(Long id, NoticeUpdateRequest req) {
        Notice notice = load(id);
        checkVersion(notice, req.version());
        notice.update(req.title(), req.content(), Boolean.TRUE.equals(req.isPinned()));
        contentTagService.replaceLinks(TYPE, id, req.tagIds());
        repository.flush(); // 버전 UPDATE 즉시 반영 → 응답 version이 post-increment (설계 §5)
        return detail(notice);
    }

    @CacheEvict(value = "main", allEntries = true)
    @Transactional
    public NoticeDetailResponse patch(Long id, NoticePatchRequest req) {
        Notice notice = load(id);
        checkVersion(notice, req.version());
        notice.applyPatch(req.title(), req.content(), req.isPinned());
        if (req.tagIds() != null) {
            contentTagService.replaceLinks(TYPE, id, req.tagIds());
        }
        repository.flush(); // 태그 미변경 PATCH도 버전 증가를 응답에 반영 (설계 §5)
        return detail(notice);
    }

    @CacheEvict(value = "main", allEntries = true)
    @Transactional
    public void delete(Long id) {
        Notice notice = load(id);
        notice.softDelete();
        contentTagService.cleanUp(TYPE, id);
    }

    private Notice load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void checkVersion(Notice notice, Long expected) {
        if (!notice.getVersion().equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private NoticeDetailResponse detail(Notice n) {
        return detail(n, n.getViewCount());
    }

    private NoticeDetailResponse detail(Notice n, long viewCount) {
        return new NoticeDetailResponse(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.isPinned(),
                viewCount,
                n.getCreatedAt(),
                n.getUpdatedAt(),
                n.getVersion(),
                contentTagService.getTags(TYPE, n.getId()),
                authorDisplayService.displayName(n.getUpdatedBy()));
    }
}
