package com.elipair.church.domain.bulletin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.bulletin.dto.BulletinCardResponse;
import com.elipair.church.domain.media.MediaService;
import com.elipair.church.domain.media.dto.MediaResponse;
import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class BulletinServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 10, 11, 12, 13};

    private BulletinRepository repository;
    private MediaService mediaService;
    private AuthorDisplayService authorDisplayService;
    private BulletinService service;

    @BeforeEach
    void init() {
        repository = mock(BulletinRepository.class);
        mediaService = mock(MediaService.class);
        authorDisplayService = mock(AuthorDisplayService.class);
        service = new BulletinService(repository, mediaService, authorDisplayService);
        when(authorDisplayService.displayName(any())).thenReturn("관리목사");
    }

    private Bulletin mockBulletin(long version) {
        Bulletin b = mock(Bulletin.class);
        when(b.getId()).thenReturn(10L);
        when(b.getVersion()).thenReturn(version);
        return b;
    }

    private MultipartFile pdfFile() {
        return new MockMultipartFile("file", "b.pdf", "application/pdf", PDF);
    }

    private MediaResponse pdfMedia(long id) {
        return new MediaResponse(id, "b.pdf", "application/pdf", 1L, 1L, null);
    }

    // ---- create ----

    @Test
    void create_with_file_uploads_pdf_and_saves() {
        when(mediaService.uploadPdf(any(), any())).thenReturn(pdfMedia(99L));
        Bulletin saved = mockBulletin(0L);
        when(repository.save(any(Bulletin.class))).thenReturn(saved);
        MultipartFile file = pdfFile();

        service.create("2026 부활절 주보", DATE, file, null, 1L);

        verify(mediaService).uploadPdf(file, 1L);
        verify(repository).save(any(Bulletin.class));
    }

    @Test
    void create_with_mediaId_requires_pdf_and_saves() {
        Bulletin saved = mockBulletin(0L);
        when(repository.save(any(Bulletin.class))).thenReturn(saved);

        service.create("주보", DATE, null, 55L, 1L);

        verify(mediaService).requirePdf(55L);
        verify(mediaService, never()).uploadPdf(any(), any());
        verify(repository).save(any(Bulletin.class));
    }

    @Test
    void create_rejects_both_file_and_mediaId_without_upload() {
        assertThatThrownBy(() -> service.create("주보", DATE, pdfFile(), 55L, 1L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(mediaService, never()).uploadPdf(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejects_neither_file_nor_mediaId_without_upload() {
        assertThatThrownBy(() -> service.create("주보", DATE, null, null, 1L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(mediaService, never()).uploadPdf(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejects_blank_title_before_upload() {
        assertThatThrownBy(() -> service.create("  ", DATE, pdfFile(), null, 1L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(mediaService, never()).uploadPdf(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejects_null_serviceDate_before_upload() {
        assertThatThrownBy(() -> service.create("주보", null, pdfFile(), null, 1L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(mediaService, never()).uploadPdf(any(), any());
        verify(repository, never()).save(any());
    }

    // ---- patch ----

    @Test
    void patch_metadata_only_flushes_without_media_calls() {
        Bulletin b0 = mockBulletin(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(b0));

        service.patch(10L, 0L, "수정 제목", DATE, null, null, 1L);

        verify(mediaService, never()).uploadPdf(any(), any());
        verify(mediaService, never()).requirePdf(any());
        verify(repository).flush();
    }

    @Test
    void patch_replaces_pdf_with_file() {
        Bulletin b0 = mockBulletin(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(b0));
        when(mediaService.uploadPdf(any(), any())).thenReturn(pdfMedia(77L));
        MultipartFile file = pdfFile();

        service.patch(10L, 0L, null, null, file, null, 1L);

        verify(mediaService).uploadPdf(file, 1L);
        verify(repository).flush();
    }

    @Test
    void patch_stale_version_throws_409_and_never_uploads() {
        Bulletin b3 = mockBulletin(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(b3));

        assertThatThrownBy(() -> service.patch(10L, 2L, null, null, pdfFile(), null, 1L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(mediaService, never()).uploadPdf(any(), any());
        verify(repository, never()).flush();
    }

    @Test
    void patch_missing_version_throws_400_before_load() {
        assertThatThrownBy(() -> service.patch(10L, null, "제목", DATE, null, null, 1L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(repository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void patch_unknown_id_throws_404() {
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patch(99L, 0L, "제목", DATE, null, null, 1L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ---- delete / get / list ----

    @Test
    void delete_soft_deletes() {
        Bulletin b = mockBulletin(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(b));

        service.delete(10L);

        verify(b).softDelete();
    }

    @Test
    void get_unknown_throws_404() {
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void list_maps_author_from_updated_by() {
        Bulletin b = mockBulletin(0L);
        when(b.getUpdatedBy()).thenReturn(7L);
        when(b.getTitle()).thenReturn("주보");
        when(b.getServiceDate()).thenReturn(DATE);
        when(b.getMediaId()).thenReturn(99L);
        Page<Bulletin> page = new PageImpl<>(List.of(b));
        when(repository.findByDeletedAtIsNull(any(Pageable.class))).thenReturn(page);
        when(authorDisplayService.displayNames(any())).thenReturn(Map.of(7L, "관리목사"));

        Page<BulletinCardResponse> result = service.list(Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).author()).isEqualTo("관리목사");
        assertThat(result.getContent().get(0).mediaId()).isEqualTo(99L);
    }
}
