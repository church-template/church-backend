package com.elipair.church.domain.gallery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.media.MediaService;
import com.elipair.church.domain.media.dto.MediaResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class GalleryPhotoServiceTest {

    private GalleryAlbumRepository albumRepository;
    private GalleryPhotoRepository photoRepository;
    private MediaService mediaService;
    private GalleryAlbumService albumService;
    private GalleryPhotoService service;

    @BeforeEach
    void init() {
        albumRepository = mock(GalleryAlbumRepository.class);
        photoRepository = mock(GalleryPhotoRepository.class);
        mediaService = mock(MediaService.class);
        albumService = mock(GalleryAlbumService.class);
        service = new GalleryPhotoService(albumRepository, photoRepository, mediaService, albumService);
        when(albumRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(GalleryAlbum.class)));
        when(photoRepository.findMaxSortOrder(1L)).thenReturn(-1); // 빈 앨범
    }

    @Test
    void addPhotos_unknown_album_is_404() {
        when(albumRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addPhotos(99L, null, List.of(5L), 7L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void addPhotos_existing_mediaIds_validated_and_appended() {
        service.addPhotos(1L, null, List.of(5L, 6L), 7L);

        verify(mediaService).requireImages(List.of(5L, 6L));
        ArgumentCaptor<GalleryPhoto> captor = ArgumentCaptor.forClass(GalleryPhoto.class);
        verify(photoRepository, times(2)).save(captor.capture());
        List<GalleryPhoto> saved = captor.getAllValues();
        assertThat(saved.get(0).getMediaId()).isEqualTo(5L);
        assertThat(saved.get(0).getSortOrder()).isEqualTo(0); // max(-1)+1
        assertThat(saved.get(1).getMediaId()).isEqualTo(6L);
        assertThat(saved.get(1).getSortOrder()).isEqualTo(1);
        verify(albumService).get(1L); // detail 반환
    }

    @Test
    void addPhotos_uploads_new_files_via_uploadImage() {
        MockMultipartFile f = new MockMultipartFile("files", "p.jpg", "image/jpeg", new byte[] {1, 2, 3});
        when(mediaService.uploadImage(eq(f), eq(7L)))
                .thenReturn(new MediaResponse(99L, "p.jpg", "image/jpeg", 3L, 7L, LocalDateTime.now()));

        service.addPhotos(1L, List.of(f), null, 7L);

        ArgumentCaptor<GalleryPhoto> captor = ArgumentCaptor.forClass(GalleryPhoto.class);
        verify(photoRepository).save(captor.capture());
        assertThat(captor.getValue().getMediaId()).isEqualTo(99L);
    }

    @Test
    void addPhotos_mixed_appends_mediaIds_then_files_in_order() {
        MockMultipartFile f = new MockMultipartFile("files", "p.jpg", "image/jpeg", new byte[] {1, 2, 3});
        when(mediaService.uploadImage(any(MultipartFile.class), eq(7L)))
                .thenReturn(new MediaResponse(99L, "p.jpg", "image/jpeg", 3L, 7L, LocalDateTime.now()));

        service.addPhotos(1L, List.of(f), List.of(5L), 7L);

        ArgumentCaptor<GalleryPhoto> captor = ArgumentCaptor.forClass(GalleryPhoto.class);
        verify(photoRepository, times(2)).save(captor.capture());
        // mediaIds(5L, sort 0) → files(99L, sort 1)
        assertThat(captor.getAllValues().get(0).getMediaId()).isEqualTo(5L);
        assertThat(captor.getAllValues().get(0).getSortOrder()).isEqualTo(0);
        assertThat(captor.getAllValues().get(1).getMediaId()).isEqualTo(99L);
        assertThat(captor.getAllValues().get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    void addPhotos_non_image_existing_id_rejected_before_saving_photos() {
        // requireImages가 비이미지로 던지면 사진 저장이 일어나지 않아야 한다.
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE))
                .when(mediaService)
                .requireImages(List.of(5L));

        assertThatThrownBy(() -> service.addPhotos(1L, null, List.of(5L), 7L)).isInstanceOf(BusinessException.class);
        verify(photoRepository, never()).save(any());
    }

    @Test
    void removePhoto_hard_deletes_and_keeps_media() {
        GalleryPhoto photo = mock(GalleryPhoto.class);
        when(photoRepository.findById(3L)).thenReturn(Optional.of(photo));

        service.removePhoto(3L);

        verify(photoRepository).delete(photo); // hard delete, media 원본 보존(별도 조작 없음)
    }

    @Test
    void removePhoto_unknown_is_404() {
        when(photoRepository.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.removePhoto(3L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
