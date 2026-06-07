package com.elipair.church.domain.gallery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.gallery.dto.GalleryAlbumCreateRequest;
import com.elipair.church.domain.gallery.dto.GalleryAlbumPatchRequest;
import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GalleryAlbumServiceTest {

    private GalleryAlbumRepository repository;
    private GalleryPhotoRepository photoRepository;
    private ContentTagService contentTagService;
    private AuthorDisplayService authorDisplayService;
    private GalleryAlbumService service;

    @BeforeEach
    void init() {
        repository = mock(GalleryAlbumRepository.class);
        photoRepository = mock(GalleryPhotoRepository.class);
        contentTagService = mock(ContentTagService.class);
        authorDisplayService = mock(AuthorDisplayService.class);
        service = new GalleryAlbumService(repository, photoRepository, contentTagService, authorDisplayService);
        when(contentTagService.getTags(any(), any())).thenReturn(List.of());
        when(authorDisplayService.displayName(any())).thenReturn("관리자");
        when(photoRepository.findByAlbumIdOrderBySortOrderAscIdAsc(any())).thenReturn(List.of());
    }

    private GalleryAlbum mockAlbum(long version) {
        GalleryAlbum a = mock(GalleryAlbum.class);
        when(a.getId()).thenReturn(10L);
        when(a.getVersion()).thenReturn(version);
        return a;
    }

    @Test
    void create_persists_and_links_tags() {
        GalleryAlbum saved = mockAlbum(0L);
        when(repository.save(any(GalleryAlbum.class))).thenReturn(saved);

        service.create(new GalleryAlbumCreateRequest("부활절", "본문", List.of(1L, 2L)));

        verify(repository).save(any(GalleryAlbum.class));
        verify(contentTagService).replaceLinks(ContentResourceType.GALLERY_ALBUM, 10L, List.of(1L, 2L));
    }

    @Test
    void patch_with_matching_version_replaces_tags_and_flushes() {
        GalleryAlbum a = mockAlbum(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));

        service.patch(10L, new GalleryAlbumPatchRequest("새제목", null, List.of(5L), 3L));

        verify(contentTagService).replaceLinks(ContentResourceType.GALLERY_ALBUM, 10L, List.of(5L));
        verify(repository).flush();
    }

    @Test
    void patch_with_null_tagIds_keeps_tags_and_flushes() {
        GalleryAlbum a = mockAlbum(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));

        service.patch(10L, new GalleryAlbumPatchRequest("새제목", null, null, 0L));

        verify(contentTagService, never()).replaceLinks(any(), any(), any());
        verify(repository).flush();
    }

    @Test
    void patch_with_stale_version_throws_409_and_skips_changes() {
        GalleryAlbum a = mockAlbum(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.patch(10L, new GalleryAlbumPatchRequest("새제목", null, List.of(5L), 2L)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(contentTagService, never()).replaceLinks(any(), any(), any());
        verify(repository, never()).flush();
    }

    @Test
    void delete_soft_deletes_cleans_tags_and_removes_photo_links() {
        GalleryAlbum a = mockAlbum(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(a));

        service.delete(10L);

        verify(a).softDelete();
        verify(contentTagService).cleanUp(ContentResourceType.GALLERY_ALBUM, 10L);
        verify(photoRepository).deleteByAlbumId(10L); // FK 안전(설계 Critical)
    }

    @Test
    void get_unknown_throws_404() {
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
