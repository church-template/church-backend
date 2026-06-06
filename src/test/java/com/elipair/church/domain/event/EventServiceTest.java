package com.elipair.church.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.event.dto.EventCreateRequest;
import com.elipair.church.domain.event.dto.EventPatchRequest;
import com.elipair.church.domain.event.dto.EventUpdateRequest;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 10, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 6, 10, 11, 0);

    private EventRepository repository;
    private ContentTagService contentTagService;
    private EventService service;

    @BeforeEach
    void init() {
        repository = mock(EventRepository.class);
        contentTagService = mock(ContentTagService.class);
        service = new EventService(repository, contentTagService);
        when(contentTagService.getTags(any(), any())).thenReturn(List.of());
    }

    private Event mockEvent(long version) {
        Event e = mock(Event.class);
        when(e.getId()).thenReturn(10L);
        when(e.getVersion()).thenReturn(version);
        return e;
    }

    private EventCreateRequest createReq() {
        return new EventCreateRequest("행사", "본문", "본당", START, END, false, List.of(1L, 2L));
    }

    @Test
    void create_persists_and_links_tags() {
        Event saved = mockEvent(0L);
        when(repository.save(any(Event.class))).thenReturn(saved);

        service.create(createReq());

        verify(repository).save(any(Event.class));
        verify(contentTagService).replaceLinks(ContentResourceType.EVENT, 10L, List.of(1L, 2L));
    }

    @Test
    void update_with_matching_version_replaces_tags_and_flushes() {
        Event e = mockEvent(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));
        EventUpdateRequest req = new EventUpdateRequest("새행사", "새본문", "교육관", START, END, true, List.of(5L), 3L);

        service.update(10L, req);

        verify(e).update("새행사", "새본문", "교육관", START, END, true);
        verify(contentTagService).replaceLinks(ContentResourceType.EVENT, 10L, List.of(5L));
        verify(repository).flush();
    }

    @Test
    void update_with_stale_version_throws_409_and_skips_changes() {
        Event e = mockEvent(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));
        EventUpdateRequest req = new EventUpdateRequest("새행사", "새본문", "교육관", START, END, true, List.of(5L), 2L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(contentTagService, never()).replaceLinks(any(), any(), any());
    }

    @Test
    void patch_with_null_tagIds_keeps_tags_and_flushes() {
        Event e = mockEvent(0L);
        when(e.getStartAt()).thenReturn(START);
        when(e.getEndAt()).thenReturn(END);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));
        EventPatchRequest req = new EventPatchRequest("부분제목", null, null, null, null, null, null, 0L);

        service.patch(10L, req);

        verify(contentTagService, never()).replaceLinks(any(), any(), any());
        verify(repository).flush();
    }

    @Test
    void patch_with_stale_version_throws_409() {
        Event e = mockEvent(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));
        EventPatchRequest req = new EventPatchRequest("부분제목", null, null, null, null, null, null, 2L);

        assertThatThrownBy(() -> service.patch(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    void patch_with_end_before_start_throws_400_and_skips_mutation() {
        Event e = mockEvent(0L);
        when(e.getStartAt()).thenReturn(START); // 기존 시작 6/10 10:00
        when(e.getEndAt()).thenReturn(END);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));
        // 새 end를 기존 start보다 이전으로(6/10 09:00) → 교차검증 실패.
        EventPatchRequest req =
                new EventPatchRequest(null, null, null, null, LocalDateTime.of(2026, 6, 10, 9, 0), null, null, 0L);

        assertThatThrownBy(() -> service.patch(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(e, never()).applyPatch(any(), any(), any(), any(), any(), any());
        verify(repository, never()).flush();
    }

    @Test
    void delete_soft_deletes_and_cleans_tags() {
        Event e = mockEvent(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));

        service.delete(10L);

        verify(e).softDelete();
        verify(contentTagService).cleanUp(ContentResourceType.EVENT, 10L);
    }

    @Test
    void get_unknown_throws_404() {
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void get_returns_detail_for_existing() {
        Event e = mockEvent(0L);
        when(e.getStartAt()).thenReturn(START);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));

        assertThat(service.get(10L).id()).isEqualTo(10L);
        verify(repository).findByIdAndDeletedAtIsNull(10L);
    }
}
