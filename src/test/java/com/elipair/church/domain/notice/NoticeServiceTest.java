package com.elipair.church.domain.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.domain.notice.dto.NoticeCreateRequest;
import com.elipair.church.domain.notice.dto.NoticePatchRequest;
import com.elipair.church.domain.notice.dto.NoticeUpdateRequest;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class NoticeServiceTest {

    private NoticeRepository repository;
    private ContentTagService contentTagService;
    private AuthorDisplayService authorDisplayService;
    private NoticeService service;

    @BeforeEach
    void init() {
        repository = mock(NoticeRepository.class);
        contentTagService = mock(ContentTagService.class);
        authorDisplayService = mock(AuthorDisplayService.class);
        service = new NoticeService(repository, contentTagService, authorDisplayService);
        when(contentTagService.getTags(any(), any())).thenReturn(List.of());
        when(authorDisplayService.displayName(any())).thenReturn("관리자");
    }

    private NoticeCreateRequest createReq() {
        return new NoticeCreateRequest("제목", "본문", false, List.of(1L, 2L));
    }

    private Notice mockNoticeWithVersion(long version) {
        Notice n = mock(Notice.class);
        when(n.getId()).thenReturn(10L);
        when(n.getVersion()).thenReturn(version);
        return n;
    }

    @Test
    void create_persists_and_links_tags() {
        Notice saved = mockNoticeWithVersion(0L);
        when(repository.save(any(Notice.class))).thenReturn(saved);

        service.create(createReq());

        verify(repository).save(any(Notice.class));
        verify(contentTagService).replaceLinks(ContentResourceType.NOTICE, 10L, List.of(1L, 2L));
    }

    @Test
    void update_with_matching_version_replaces_tags_and_flushes() {
        Notice n = mockNoticeWithVersion(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));
        NoticeUpdateRequest req = new NoticeUpdateRequest("새제목", "새본문", true, List.of(5L), 3L);

        service.update(10L, req);

        verify(contentTagService).replaceLinks(ContentResourceType.NOTICE, 10L, List.of(5L));
        verify(repository).flush();
    }

    @Test
    void update_with_stale_version_throws_409_and_skips_changes() {
        Notice n = mockNoticeWithVersion(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));
        NoticeUpdateRequest req = new NoticeUpdateRequest("새제목", "새본문", true, List.of(5L), 2L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(contentTagService, never()).replaceLinks(any(), any(), any());
        verify(repository, never()).flush();
    }

    @Test
    void patch_with_null_tagIds_keeps_tags_and_flushes() {
        Notice n = mockNoticeWithVersion(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));
        NoticePatchRequest req = new NoticePatchRequest("부분제목", null, null, null, 0L);

        service.patch(10L, req);

        verify(contentTagService, never()).replaceLinks(any(), any(), any());
        verify(repository).flush();
    }

    @Test
    void patch_with_stale_version_throws_409_and_skips_changes() {
        Notice n = mockNoticeWithVersion(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));
        NoticePatchRequest req = new NoticePatchRequest("부분제목", null, null, null, 2L);

        assertThatThrownBy(() -> service.patch(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(contentTagService, never()).replaceLinks(any(), any(), any());
        verify(repository, never()).flush();
    }

    @Test
    void delete_soft_deletes_and_cleans_tags() {
        Notice n = mockNoticeWithVersion(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));

        service.delete(10L);

        verify(n).softDelete();
        verify(contentTagService).cleanUp(ContentResourceType.NOTICE, 10L);
    }

    @Test
    void get_unknown_throws_404() {
        when(repository.incrementViewCount(99L)).thenReturn(0);
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void get_increments_view_count_before_loading() {
        Notice n = mockNoticeWithVersion(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));

        service.get(10L);

        InOrder order = inOrder(repository);
        order.verify(repository).incrementViewCount(10L);
        order.verify(repository).findByIdAndDeletedAtIsNull(10L);
    }
}
