package com.elipair.church.domain.gallery;

import com.elipair.church.domain.gallery.dto.GalleryAlbumDetailResponse;
import com.elipair.church.domain.media.MediaService;
import com.elipair.church.domain.media.dto.MediaResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 갤러리 사진 서비스(스펙 §5.12). 추가는 앨범 행을 비관락으로 잡아 sort_order append 경합을 막고(설계 Major),
 * 기존 mediaIds는 requireImages, 신규 파일은 uploadImage(저장 전 이미지 검증)로 받는다.
 * 해제는 연결행 hard delete(media 원본 보존). detail 반환은 albumService에 위임(단방향 의존).
 */
@Service
@Transactional(readOnly = true)
public class GalleryPhotoService {

    private final GalleryAlbumRepository albumRepository;
    private final GalleryPhotoRepository photoRepository;
    private final MediaService mediaService;
    private final GalleryAlbumService albumService;

    public GalleryPhotoService(
            GalleryAlbumRepository albumRepository,
            GalleryPhotoRepository photoRepository,
            MediaService mediaService,
            GalleryAlbumService albumService) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.mediaService = mediaService;
        this.albumService = albumService;
    }

    @Transactional
    public GalleryAlbumDetailResponse addPhotos(
            Long albumId, List<MultipartFile> files, List<Long> mediaIds, Long uploaderId) {
        // 앨범 행 비관락 로드 — 동시 추가 직렬화(미존재/삭제 시 404). 락은 트랜잭션 종료 시 자동 해제.
        albumRepository
                .findByIdForUpdate(albumId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<Long> existing = mediaIds == null ? List.of() : mediaIds;
        mediaService.requireImages(existing); // 기존 mediaIds 존재+이미지 검증(fail-fast, 업로드 전)

        int next = photoRepository.findMaxSortOrder(albumId) + 1;
        for (Long mediaId : existing) {
            photoRepository.save(GalleryPhoto.create(albumId, mediaId, next++));
        }
        if (files != null) {
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                MediaResponse uploaded = mediaService.uploadImage(file, uploaderId); // 저장 전 이미지 검증
                photoRepository.save(GalleryPhoto.create(albumId, uploaded.id(), next++));
            }
        }
        return albumService.get(albumId);
    }

    @Transactional
    public void removePhoto(Long photoId) {
        GalleryPhoto photo = photoRepository
                .findById(photoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        photoRepository.delete(photo); // 연결 해제 = hard delete. media 원본은 라이브러리에 유지.
    }
}
