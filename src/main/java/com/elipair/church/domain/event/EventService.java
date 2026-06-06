package com.elipair.church.domain.event;

import com.elipair.church.domain.event.dto.EventCardResponse;
import com.elipair.church.domain.event.dto.EventCreateRequest;
import com.elipair.church.domain.event.dto.EventDetailResponse;
import com.elipair.church.domain.event.dto.EventPatchRequest;
import com.elipair.church.domain.event.dto.EventUpdateRequest;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정 서비스(스펙 §5.6). 태그(ContentTagService)와 조립. 작성자 미노출·조회수 없음(설계 §1).
 * 낙관락은 명시적 version 비교(백스톱 JPA @Version). 엔티티 필드 변경 update/patch는 flush로 응답 version 정합;
 * tag-only 수정은 events 행 미변경이라 version 유지(설계 §5 Finding 2). PATCH의 start/end 교차검증은 서비스 책임(설계 §5.1).
 */
@Service
@Transactional(readOnly = true)
public class EventService {

    private static final ContentResourceType TYPE = ContentResourceType.EVENT;

    private final EventRepository repository;
    private final ContentTagService contentTagService;

    public EventService(EventRepository repository, ContentTagService contentTagService) {
        this.repository = repository;
        this.contentTagService = contentTagService;
    }

    public Page<EventCardResponse> list(DateRange range, Long tagId, Pageable pageable) {
        List<Long> taggedIds = tagId == null ? null : contentTagService.resourceIdsWithTag(TYPE, tagId);
        Page<Event> page = repository.findAll(EventSpecifications.filter(range, taggedIds), pageable);

        List<Long> ids = page.map(Event::getId).getContent();
        Map<Long, List<TagResponse>> tagsMap = contentTagService.getTagsByResources(TYPE, ids);

        return page.map(e -> new EventCardResponse(
                e.getId(),
                e.getTitle(),
                e.getLocation(),
                e.getStartAt(),
                e.getEndAt(),
                e.isAllDay(),
                tagsMap.getOrDefault(e.getId(), List.of())));
    }

    public EventDetailResponse get(Long id) {
        return detail(load(id));
    }

    @Transactional
    public EventDetailResponse create(EventCreateRequest req) {
        Event event = repository.save(Event.create(
                req.title(),
                req.description(),
                req.location(),
                req.startAt(),
                req.endAt(),
                Boolean.TRUE.equals(req.allDay())));
        contentTagService.replaceLinks(TYPE, event.getId(), req.tagIds());
        return detail(event);
    }

    @Transactional
    public EventDetailResponse update(Long id, EventUpdateRequest req) {
        Event event = load(id);
        checkVersion(event, req.version());
        event.update(
                req.title(),
                req.description(),
                req.location(),
                req.startAt(),
                req.endAt(),
                Boolean.TRUE.equals(req.allDay()));
        contentTagService.replaceLinks(TYPE, id, req.tagIds());
        repository.flush(); // 엔티티 필드 변경분의 버전 UPDATE 즉시 반영 (설계 §5)
        return detail(event);
    }

    @Transactional
    public EventDetailResponse patch(Long id, EventPatchRequest req) {
        Event event = load(id);
        checkVersion(event, req.version());
        LocalDateTime effectiveStart = req.startAt() != null ? req.startAt() : event.getStartAt();
        LocalDateTime effectiveEnd = req.endAt() != null ? req.endAt() : event.getEndAt();
        if (effectiveEnd != null && !effectiveEnd.isAfter(effectiveStart)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE); // 교차검증(설계 §5.1)
        }
        event.applyPatch(req.title(), req.description(), req.location(), req.startAt(), req.endAt(), req.allDay());
        if (req.tagIds() != null) {
            contentTagService.replaceLinks(TYPE, id, req.tagIds());
        }
        repository.flush();
        return detail(event);
    }

    @Transactional
    public void delete(Long id) {
        Event event = load(id);
        event.softDelete();
        contentTagService.cleanUp(TYPE, id);
    }

    private Event load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void checkVersion(Event event, Long expected) {
        if (!event.getVersion().equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private EventDetailResponse detail(Event e) {
        return new EventDetailResponse(
                e.getId(),
                e.getTitle(),
                e.getDescription(),
                e.getLocation(),
                e.getStartAt(),
                e.getEndAt(),
                e.isAllDay(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion(),
                contentTagService.getTags(TYPE, e.getId()));
    }
}
