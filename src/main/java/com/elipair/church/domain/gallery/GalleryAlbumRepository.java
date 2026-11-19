package com.elipair.church.domain.gallery;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GalleryAlbumRepository
        extends JpaRepository<GalleryAlbum, Long>, JpaSpecificationExecutor<GalleryAlbum> {

    Optional<GalleryAlbum> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 사진 추가 시 앨범 행을 비관적 쓰기 락으로 로드(설계 §6) — 같은 앨범 동시 추가를 직렬화해
     * max(sort_order)+1 경합(중복 순서)을 차단. 트랜잭션 종료 시 자동 해제.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GalleryAlbum a where a.id = :id and a.deletedAt is null")
    Optional<GalleryAlbum> findByIdForUpdate(@Param("id") Long id);

    /**
     * 본문(description)이 media:{id}를 참조하는 미삭제 앨범(id·title). PG 정규식 ~ 로 경계 안전 매칭.
     * pattern 예: "media:42($|[^0-9])" — 42가 media:420/421에 매칭되지 않는다.
     */
    @Query(
            value =
                    "select id as id, title as title from gallery_albums where church_id = :churchId and deleted_at is null and description ~ :pattern",
            nativeQuery = true)
    List<GalleryAlbumRefRow> findReferencesByMedia(@Param("pattern") String pattern, @Param("churchId") long churchId);

    /** 공개 판단용 — 설명에서 이 미디어를 쓰는 "교인만"이 아닌 미삭제 앨범이 있는가. */
    @Query(
            value = "select exists(select 1 from gallery_albums where church_id = :churchId and deleted_at is null"
                    + " and members_only = false and description ~ :pattern)",
            nativeQuery = true)
    boolean existsPublicReferenceByMedia(@Param("pattern") String pattern, @Param("churchId") long churchId);
}
