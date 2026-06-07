package com.elipair.church.domain.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.media.dto.MediaReferencesResponse;
import com.elipair.church.domain.media.dto.MediaResponse;
import com.elipair.church.global.common.ContentRef;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.exception.MediaInUseException;
import com.elipair.church.global.storage.FileStorage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private MediaRepository repository;

    @Mock
    private FileStorage fileStorage;

    private final MimeTypeValidator mimeTypeValidator = new MimeTypeValidator();

    private MediaService service(List<MediaReferenceProvider> providers) {
        return new MediaService(repository, fileStorage, mimeTypeValidator, providers);
    }

    private Media media() {
        return Media.create("a.jpg", "2026/06/a.jpg", "image/jpeg", 100L, 1L);
    }

    @Test
    void upload_stores_with_sniffed_mime_ignoring_header() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
        // 헤더는 image/png라 거짓말하지만 실제 바이트는 JPEG → 저장 mime은 스니핑 결과여야 한다
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/png", jpeg);
        when(fileStorage.store(file)).thenReturn("2026/06/x.jpg");
        when(repository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaResponse res = service(List.of()).upload(file, 7L);

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(repository).save(captor.capture());
        Media saved = captor.getValue();
        assertThat(saved.getMimeType()).isEqualTo("image/jpeg");
        assertThat(saved.getStoredPath()).isEqualTo("2026/06/x.jpg");
        assertThat(saved.getFilename()).isEqualTo("photo.jpg");
        assertThat(saved.getUploadedBy()).isEqualTo(7L);
        assertThat(saved.getSize()).isEqualTo(jpeg.length);
        assertThat(res.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void upload_cleans_up_file_when_db_save_fails() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg);
        when(fileStorage.store(file)).thenReturn("2026/06/x.jpg");
        when(repository.save(any(Media.class))).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> service(List.of()).upload(file, 1L))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(fileStorage).delete("2026/06/x.jpg"); // 고아 파일 best-effort 정리
    }

    @Test
    void upload_rejects_unsupported_bytes_without_storing() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.exe", "image/jpeg", new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11});

        assertThatThrownBy(() -> service(List.of()).upload(file, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(fileStorage);
        verify(repository, never()).save(any());
    }

    @Test
    void delete_without_references_removes_record_and_file() {
        Media media = media();
        when(repository.findById(5L)).thenReturn(Optional.of(media));

        service(List.of()).delete(5L);

        verify(repository).delete(media);
        verify(fileStorage).delete("2026/06/a.jpg");
    }

    @Test
    void delete_with_references_is_blocked_and_keeps_record_and_file() {
        when(repository.findById(5L)).thenReturn(Optional.of(media()));
        MediaReferenceProvider provider = mediaId -> List.of(new ContentRef("notice", 7L, "공지"));

        assertThatThrownBy(() -> service(List.of(provider)).delete(5L)).isInstanceOf(MediaInUseException.class);

        verify(repository, never()).delete(any(Media.class));
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void references_unions_all_providers() {
        when(repository.findById(5L)).thenReturn(Optional.of(media()));
        MediaReferenceProvider p1 = mediaId -> List.of(new ContentRef("notice", 7L, "공지"));
        MediaReferenceProvider p2 = mediaId -> List.of(new ContentRef("sermon", 15L, "설교"));

        MediaReferencesResponse res = service(List.of(p1, p2)).references(5L);

        assertThat(res.inUse()).isTrue();
        assertThat(res.references()).hasSize(2);
        assertThat(res.mediaId()).isEqualTo(5L);
    }

    @Test
    void references_empty_when_no_providers() {
        when(repository.findById(5L)).thenReturn(Optional.of(media()));

        MediaReferencesResponse res = service(List.of()).references(5L);

        assertThat(res.inUse()).isFalse();
        assertThat(res.references()).isEmpty();
    }

    @Test
    void list_rejects_unknown_type() {
        assertThatThrownBy(() -> service(List.of()).list("foo", null, null, Pageable.unpaged()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void get_unknown_id_is_404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(List.of()).get(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 10, 11, 12, 13};

    @Test
    void uploadImage_stores_image_and_returns_response() {
        MockMultipartFile file = new MockMultipartFile("file", "p.jpg", "application/octet-stream", JPEG);
        when(fileStorage.store(file)).thenReturn("2026/06/x.jpg");
        when(repository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaResponse res = service(List.of()).uploadImage(file, 7L);

        assertThat(res.mimeType()).isEqualTo("image/jpeg");
        verify(repository).save(any(Media.class));
    }

    @Test
    void uploadImage_rejects_pdf_before_storing() {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "image/jpeg", PDF);

        assertThatThrownBy(() -> service(List.of()).uploadImage(file, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(fileStorage); // 저장 전 거부 → 고아 파일 없음
        verify(repository, never()).save(any());
    }

    @Test
    void requireImages_passes_when_all_are_images() {
        when(repository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(
                        Media.create("a.jpg", "p1", "image/jpeg", 1L, 1L),
                        Media.create("b.png", "p2", "image/png", 1L, 1L)));

        service(List.of()).requireImages(List.of(1L, 2L)); // 예외 없음
    }

    @Test
    void requireImages_throws_404_when_some_missing() {
        when(repository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(Media.create("a.jpg", "p1", "image/jpeg", 1L, 1L)));

        assertThatThrownBy(() -> service(List.of()).requireImages(List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void requireImages_throws_400_when_a_pdf_is_included() {
        when(repository.findAllById(List.of(1L)))
                .thenReturn(List.of(Media.create("d.pdf", "p", "application/pdf", 1L, 1L)));

        assertThatThrownBy(() -> service(List.of()).requireImages(List.of(1L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void requireImages_noop_on_empty() {
        service(List.of()).requireImages(List.of()); // 예외 없음, 조회 안 함
        verifyNoInteractions(repository);
    }
}
