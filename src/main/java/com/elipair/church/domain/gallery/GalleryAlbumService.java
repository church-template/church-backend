package com.elipair.church.domain.gallery;

import com.elipair.church.domain.department.DepartmentRepository;
import com.elipair.church.domain.gallery.dto.GalleryAlbumCardResponse;
import com.elipair.church.domain.gallery.dto.GalleryAlbumCreateRequest;
import com.elipair.church.domain.gallery.dto.GalleryAlbumDetailResponse;
import com.elipair.church.domain.gallery.dto.GalleryAlbumPatchRequest;
import com.elipair.church.domain.gallery.dto.GalleryPhotoResponse;
import com.elipair.church.domain.media.MediaRefs;
import com.elipair.church.domain.media.MediaService;
import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.tenant.TenantContext;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 갤러리 앨범 서비스(스펙 §5.12). 태그(ContentTagService)·작성자(AuthorDisplayService)와 조립.
 * 목록은 썸네일·사진수·태그·작성자를 배치 조회해 N+1을 피한다. 낙관락은 명시적 version 비교 + flush로 응답 정합.
 * 삭제는 앨범 soft delete + 태그 정리 + 사진 행 hard delete(media_id FK 댕글링 차단 — 설계 Critical).
 */
@Service
@Transactional(readOnly = true)
public class GalleryAlbumService {

    private static final ContentResourceType TYPE = ContentResourceType.GALLERY_ALBUM;

    private final GalleryAlbumRepository repository;
    private final GalleryPhotoRepository photoRepository;
    private final ContentTagService contentTagService;
    private final AuthorDisplayService authorDisplayService;
    private final MediaService mediaService;
    private final DepartmentRepository departmentRepository;

    public GalleryAlbumService(
            GalleryAlbumRepository repository,
            GalleryPhotoRepository photoRepository,
            ContentTagService contentTagService,
            AuthorDisplayService authorDisplayService,
            MediaService mediaService,
            DepartmentRepository departmentRepository) {
        this.repository = repository;
        this.photoRepository = photoRepository;
        this.contentTagService = contentTagService;
        this.authorDisplayService = authorDisplayService;
        this.mediaService = mediaService;
        this.departmentRepository = departmentRepository;
    }

    /** 보는 사람을 읽지 않는다 — 교인 전용 포함 여부·관리자 필터는 컨트롤러가 정해 넘긴다(설계 6.6-1). */
    public Page<GalleryAlbumCardResponse> list(
            Long tagId, boolean includeMembersOnly, Boolean membersOnlyFilter, Pageable pageable) {
        List<Long> taggedIds = tagId == null ? null : contentTagService.resourceIdsWithTag(TYPE, tagId);
        Page<GalleryAlbum> page = repository.findAll(
                GalleryAlbumSpecifications.filter(taggedIds, includeMembersOnly, membersOnlyFilter), pageable);

        List<Long> ids = page.map(GalleryAlbum::getId).getContent();
        Map<Long, List<com.elipair.church.domain.tag.dto.TagResponse>> tagsMap =
                contentTagService.getTagsByResources(TYPE, ids);
        Map<Long, String> authorMap = authorDisplayService.displayNames(
                page.map(GalleryAlbum::getUpdatedBy).getContent());
        Map<Long, Long> thumbMap = ids.isEmpty()
                ? Map.of()
                : photoRepository.findThumbnails(ids, TenantContext.require()).stream()
                        .collect(Collectors.toMap(AlbumThumbnailRow::getAlbumId, AlbumThumbnailRow::getMediaId));
        Map<Long, Long> countMap = ids.isEmpty()
                ? Map.of()
                : photoRepository.countByAlbumIds(ids).stream()
                        .collect(Collectors.toMap(AlbumPhotoCountRow::getAlbumId, AlbumPhotoCountRow::getCount));
        Set<Long> linked = ids.isEmpty() ? Set.of() : new HashSet<>(departmentRepository.findLinkedAlbumIds(ids));

        return page.map(a -> {
            Long thumbId = thumbMap.get(a.getId());
            return new GalleryAlbumCardResponse(
                    a.getId(),
                    a.getTitle(),
                    thumbId,
                    countMap.getOrDefault(a.getId(), 0L),
                    a.getCreatedAt(),
                    tagsMap.getOrDefault(a.getId(), List.of()),
                    authorMap.getOrDefault(a.getUpdatedBy(), AuthorDisplayService.UNKNOWN),
                    thumbId == null ? null : mediaService.signedUrl(thumbId),
                    a.isMembersOnly(),
                    linked.contains(a.getId()),
                    a.getVersion());
        });
    }

    /** 상세 접근 판단용. 없으면 404. */
    public boolean isMembersOnly(Long id) {
        return load(id).isMembersOnly();
    }

    /** DepartmentService용 — 부서에 연결하려는 앨범이 교인 전용인가. 없거나 삭제됐으면 false(존재 검사는 호출자 몫). */
    public boolean isMembersOnlyAlbum(Long albumId) {
        return repository
                .findByIdAndDeletedAtIsNull(albumId)
                .map(GalleryAlbum::isMembersOnly)
                .orElse(false);
    }

    /** 부서 공개 상세용 — 미삭제 앨범의 사진 mediaId를 앨범 순서(sort_order, id)로. 앨범이 없거나 삭제됐으면 empty. */
    public Optional<List<Long>> findLivePhotoMediaIds(Long albumId) {
        return repository
                .findByIdAndDeletedAtIsNull(albumId)
                .map(a -> photoRepository.findByAlbumIdOrderBySortOrderAscIdAsc(a.getId()).stream()
                        .map(GalleryPhoto::getMediaId)
                        .toList());
    }

    public GalleryAlbumDetailResponse get(Long id) {
        return detail(load(id));
    }

    @Transactional
    public GalleryAlbumDetailResponse create(GalleryAlbumCreateRequest req) {
        // 새 앨범은 아직 어느 부서에도 연결될 수 없으므로 부서 연결 검사가 필요 없다.
        GalleryAlbum draft = GalleryAlbum.create(req.title(), req.description());
        draft.changeMembersOnly(Boolean.TRUE.equals(req.membersOnly())); // 저장 전에 — 저장 뒤 바꾸면 version이 오른다
        GalleryAlbum album = repository.save(draft);
        contentTagService.replaceLinks(TYPE, album.getId(), req.tagIds());
        return detail(album);
    }

    @Transactional
    public GalleryAlbumDetailResponse patch(Long id, GalleryAlbumPatchRequest req) {
        GalleryAlbum album = load(id);
        checkVersion(album, req.version());
        album.applyPatch(req.title(), req.description());
        if (req.membersOnly() != null) {
            // 부서 소개는 연결 앨범 사진을 누구에게나 보여 준다 — 교인 전용으로 바꾸면 그 약속이 깨진다(설계 6.2)
            if (req.membersOnly() && isDepartmentLinked(id)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "부서 소개에 쓰이는 앨범은 교인 전용으로 바꿀 수 없습니다");
            }
            album.changeMembersOnly(req.membersOnly());
        }
        if (req.tagIds() != null) {
            contentTagService.replaceLinks(TYPE, id, req.tagIds());
        }
        repository.flush(); // 엔티티 필드 변경분의 버전 UPDATE 즉시 반영(tag-only는 행 미변경이라 version 유지)
        return detail(album);
    }

    @Transactional
    public void delete(Long id) {
        GalleryAlbum album = load(id);
        album.softDelete();
        contentTagService.cleanUp(TYPE, id);
        photoRepository.deleteByAlbumId(id); // 연결행 정리 — media 차단삭제가 FK 위반 없이 동작하도록(설계 Critical)
    }

    private GalleryAlbum load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void checkVersion(GalleryAlbum album, Long expected) {
        if (!album.getVersion().equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private GalleryAlbumDetailResponse detail(GalleryAlbum a) {
        List<GalleryPhotoResponse> photos = photoRepository.findByAlbumIdOrderBySortOrderAscIdAsc(a.getId()).stream()
                .map(p -> new GalleryPhotoResponse(
                        p.getId(),
                        p.getMediaId(),
                        p.getCaption(),
                        p.getSortOrder(),
                        mediaService.signedUrl(p.getMediaId())))
                .toList();
        return new GalleryAlbumDetailResponse(
                a.getId(),
                a.getTitle(),
                a.getDescription(),
                contentTagService.getTags(TYPE, a.getId()),
                authorDisplayService.displayName(a.getUpdatedBy()),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getVersion(),
                photos,
                mediaService.signedUrls(MediaRefs.ids(a.getDescription())),
                a.isMembersOnly(),
                isDepartmentLinked(a.getId()));
    }

    private boolean isDepartmentLinked(Long albumId) {
        return !departmentRepository.findLinkedAlbumIds(List.of(albumId)).isEmpty();
    }
}
