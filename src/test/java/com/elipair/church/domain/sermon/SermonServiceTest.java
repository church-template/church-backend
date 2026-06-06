package com.elipair.church.domain.sermon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.domain.sermon.dto.SermonCreateRequest;
import com.elipair.church.domain.sermon.dto.SermonPatchRequest;
import com.elipair.church.domain.sermon.dto.SermonUpdateRequest;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SermonServiceTest {

    private SermonRepository repository;
    private ContentTagService contentTagService;
    private AuthorDisplayService authorDisplayService;
    private SermonService service;

    @BeforeEach
    void init() {
        repository = mock(SermonRepository.class);
        contentTagService = mock(ContentTagService.class);
        authorDisplayService = mock(AuthorDisplayService.class);
        service = new SermonService(repository, contentTagService, authorDisplayService);
        when(contentTagService.getTags(any(), any())).thenReturn(List.of());
        when(authorDisplayService.displayName(any())).thenReturn("관리자");
    }

    private SermonCreateRequest createReq() {
        return new SermonCreateRequest(
                "제목", "김목사", "시리즈", "마 5", "본문", null, null, LocalDate.of(2026, 1, 1), List.of(1L, 2L));
    }

    private Sermon mockSermonWithVersion(long version) {
        Sermon s = mock(Sermon.class);
        when(s.getId()).thenReturn(10L);
        when(s.getVersion()).thenReturn(version);
        return s;
    }

    @Test
    void create_persists_and_links_tags() {
        Sermon saved = mockSermonWithVersion(0L);
        when(repository.save(any(Sermon.class))).thenReturn(saved);

        service.create(createReq());

        verify(repository).save(any(Sermon.class));
        verify(contentTagService).replaceLinks(ContentResourceType.SERMON, 10L, List.of(1L, 2L));
    }

    @Test
    void update_with_matching_version_replaces_tags() {
        Sermon s = mockSermonWithVersion(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(s));
        SermonUpdateRequest req = new SermonUpdateRequest(
                "새제목", "이목사", null, null, "새본문", null, null, LocalDate.of(2026, 2, 2), List.of(5L), 3L);

        service.update(10L, req);

        verify(contentTagService).replaceLinks(ContentResourceType.SERMON, 10L, List.of(5L));
    }

    @Test
    void update_with_stale_version_throws_409_and_skips_changes() {
        Sermon s = mockSermonWithVersion(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(s));
        SermonUpdateRequest req = new SermonUpdateRequest(
                "새제목", "이목사", null, null, "새본문", null, null, LocalDate.of(2026, 2, 2), List.of(5L), 2L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(contentTagService, never()).replaceLinks(any(), any(), any());
    }

    @Test
    void patch_with_null_tagIds_keeps_tags() {
        Sermon s = mockSermonWithVersion(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(s));
        SermonPatchRequest req = new SermonPatchRequest("부분제목", null, null, null, null, null, null, null, null, 0L);

        service.patch(10L, req);

        verify(contentTagService, never()).replaceLinks(any(), any(), any());
    }

    @Test
    void patch_with_stale_version_throws_409_and_skips_changes() {
        Sermon s = mockSermonWithVersion(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(s));
        SermonPatchRequest req = new SermonPatchRequest("부분제목", null, null, null, null, null, null, null, null, 2L);

        assertThatThrownBy(() -> service.patch(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(contentTagService, never()).replaceLinks(any(), any(), any());
    }

    @Test
    void delete_soft_deletes_and_cleans_tags() {
        Sermon s = mockSermonWithVersion(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(s));

        service.delete(10L);

        verify(s).softDelete();
        verify(contentTagService).cleanUp(ContentResourceType.SERMON, 10L);
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
        Sermon s = mockSermonWithVersion(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(s));

        service.get(10L);

        InOrder order = inOrder(repository);
        order.verify(repository).incrementViewCount(10L);
        order.verify(repository).findByIdAndDeletedAtIsNull(10L);
    }
}
