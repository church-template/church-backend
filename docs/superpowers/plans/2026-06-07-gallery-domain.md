# 갤러리(Gallery) 도메인 구현 계획 — #16

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원 전용(`GALLERY_VIEW`) 앨범 단위 사진 갤러리 도메인을 구현한다 — 앨범 CRUD·사진 추가/해제, 글로벌 태그·작성자 표시·낙관락, 미디어 차단삭제 2경로(앨범 본문 LIKE + 사진 FK) 합류.

**Architecture:** `domain/gallery` 패키지 신규(엔티티 2·리포지토리 2·Provider 2·서비스 2·컨트롤러 2·DTO 5·V11 마이그레이션). 앨범은 `BaseEntity`(soft delete·`@Version`), 사진은 경량 `BaseTimeEntity`(연결행, 해제=hard delete). 사진 추가 시 앨범 행을 비관락(`@Lock(PESSIMISTIC_WRITE)`)으로 잡아 `sort_order` append 경합을 막고, 신규 업로드는 저장 전 이미지 검증(`MediaService.uploadImage`)으로 고아 파일을 차단한다. 앨범 삭제는 사진 행을 hard delete해 `media_id` FK 댕글링을 막는다.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Data JPA, PostgreSQL 16(Flyway), Spring Security(JWT), JUnit 5 + Mockito + Testcontainers + MockMvc.

**설계 출처:** `docs/superpowers/specs/2026-06-07-gallery-domain-design.md` (스펙 §5.12·§5.10·§5·§6). 코드리뷰 반영분(Critical FK·Major 고아파일·Major sort_order 경합) 포함.

**선행 확인(이미 배선됨, 변경 불필요):** `GALLERY_WRITE`/`GALLERY_VIEW` 시드(`V2`), `MEMBER`→`GALLERY_VIEW` 매핑, `SecurityConfig`의 `/api/gallery/** → hasAuthority("GALLERY_VIEW")`·`/api/admin/** → authenticated()`, `ContentResourceType.GALLERY_ALBUM`. V10(departments)이 마지막 → 갤러리는 **V11**.

**공통 규약(모든 작업 공통):**
- 빌드/테스트: `./gradlew test`. 단일 클래스: `./gradlew test --tests 'com.elipair.church.domain.gallery.<클래스>'`.
- 커밋 컨벤션: `<type> : <설명> #16` (콜론 앞 공백, 한글). Co-Authored-By 금지.
- 커밋은 각 Task 끝에서 수행. push는 하지 않는다.

---

## Task 1: 미디어 이미지 전용 업로드·검증 메서드 (`MediaService`)

갤러리 사진 추가가 의존하는 media 도메인 선행 메서드 2개. `uploadImage`는 **저장 전** 이미지 검증으로 고아 파일을 막고, `requireImages`는 기존 mediaIds의 존재+이미지 검증.

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/media/MediaService.java`
- Test: `src/test/java/com/elipair/church/domain/media/MediaServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 추가**

`MediaServiceTest.java`의 마지막 `}` 앞에 아래 메서드들을 추가한다. 기존 import(`MockMultipartFile`, `verifyNoInteractions`, `never`, `BusinessException`, `ErrorCode` 등)는 이미 있다. `java.util.Collection`은 불필요(아래는 `List` 사용).

```java
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
```

> 주의: `requireImages_noop_on_empty`는 `verifyNoInteractions(repository)`를 쓰므로, 다른 테스트와 달리 `repository` stub을 두지 않는다. `@Mock repository`는 호출되지 않아야 한다.

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.media.MediaServiceTest'`
Expected: 컴파일 실패 — `uploadImage`/`requireImages` 메서드 없음.

- [ ] **Step 3: `MediaService`에 메서드 추가 + `upload` 공통화**

`MediaService.java`에서 import에 `java.util.Collection`을 추가하고, 기존 `upload(...)` 메서드를 아래로 교체(공통 저장 로직을 `persist`로 추출)한 뒤 `uploadImage`/`requireImages`를 추가한다.

기존:
```java
    @Transactional
    public MediaResponse upload(MultipartFile file, Long uploaderId) {
        String mimeType = detectMime(file); // 헤더 위조 무력화: 저장 mime_type은 스니핑 결과
        String storedPath = fileStorage.store(file);
        try {
            String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload";
            Media media = repository.save(Media.create(filename, storedPath, mimeType, file.getSize(), uploaderId));
            return MediaResponse.from(media);
        } catch (RuntimeException e) {
            // DB 저장 실패 시 방금 쓴 파일을 best-effort 정리 — 레코드 없는 고아 파일은 차단삭제로도 못 지우는 진짜 누수.
            try {
                fileStorage.delete(storedPath);
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }
```

교체 후:
```java
    @Transactional
    public MediaResponse upload(MultipartFile file, Long uploaderId) {
        return persist(file, detectMime(file), uploaderId);
    }

    /** 갤러리 사진 전용 — 저장 전에 이미지 여부를 확정해 비이미지는 파일을 쓰지 않고 거부(고아 파일 차단, 설계 §7). */
    @Transactional
    public MediaResponse uploadImage(MultipartFile file, Long uploaderId) {
        String mimeType = detectMime(file);
        if (!mimeType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미지 파일만 업로드할 수 있습니다");
        }
        return persist(file, mimeType, uploaderId);
    }

    /** 기존 라이브러리에서 고른 mediaIds가 모두 존재하고 이미지인지 검증(설계 §7). 빈 입력은 무검증 통과. */
    public void requireImages(Collection<Long> mediaIds) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }
        List<Long> distinct = mediaIds.stream().distinct().toList();
        List<Media> found = repository.findAllById(distinct);
        if (found.size() != distinct.size()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        for (Media media : found) {
            if (!media.getMimeType().startsWith("image/")) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미지 미디어만 추가할 수 있습니다");
            }
        }
    }

    private MediaResponse persist(MultipartFile file, String mimeType, Long uploaderId) {
        String storedPath = fileStorage.store(file);
        try {
            String filename = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload";
            Media media = repository.save(Media.create(filename, storedPath, mimeType, file.getSize(), uploaderId));
            return MediaResponse.from(media);
        } catch (RuntimeException e) {
            // DB 저장 실패 시 방금 쓴 파일을 best-effort 정리 — 레코드 없는 고아 파일은 차단삭제로도 못 지우는 진짜 누수.
            try {
                fileStorage.delete(storedPath);
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.media.MediaServiceTest'`
Expected: PASS (신규 5건 + 기존 전부).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/media/MediaService.java \
        src/test/java/com/elipair/church/domain/media/MediaServiceTest.java
git commit -m "feat : 미디어 이미지 전용 업로드·존재검증 메서드 추가 #16"
```

---

## Task 2: 엔티티 · 마이그레이션 V11 · 리포지토리

앨범/사진 엔티티, V11 마이그레이션, 두 리포지토리(+프로젝션), 그리고 부분 인덱스 회귀 테스트.

**Files:**
- Create: `src/main/resources/db/migration/V11__create_gallery.sql`
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryAlbum.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryPhoto.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryAlbumRepository.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryPhotoRepository.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryAlbumRefRow.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryPhotoRefRow.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/AlbumThumbnailRow.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/AlbumPhotoCountRow.java`
- Test: `src/test/java/com/elipair/church/domain/gallery/GalleryAlbumRepositoryTest.java`
- Test: `src/test/java/com/elipair/church/domain/gallery/GalleryPhotoRepositoryTest.java`
- Modify: `src/test/java/com/elipair/church/MigrationIndexTest.java`

- [ ] **Step 1: 마이그레이션 작성**

`src/main/resources/db/migration/V11__create_gallery.sql`:
```sql
-- 갤러리(스펙 §5.12). 앨범은 BaseEntity(감사/소프트삭제/낙관락) 상속 — V7~V10 관례. V10=departments 점유 → V11.
-- 사진(gallery_photos)은 경량 연결행: created_at만(BaseTimeEntity), soft delete/version 없음. 해제=hard delete.
-- 앨범 description은 마크다운 원본(TEXT), 본문 이미지는 media:{id} 참조(차단삭제 LIKE 대상).
CREATE TABLE gallery_albums (
    id          BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,
    created_by  BIGINT       REFERENCES members (id),
    updated_by  BIGINT       REFERENCES members (id),
    deleted_at  TIMESTAMP,
    version     BIGINT       NOT NULL DEFAULT 0
);

-- 앨범 목록 정렬 = created_at DESC, 미삭제만(스펙 §6 부분 인덱스).
CREATE INDEX idx_gallery_albums_created_at ON gallery_albums (created_at DESC) WHERE deleted_at IS NULL;

-- 사진: 앨범당 정렬은 (album_id, sort_order), media 차단삭제 FK 검색은 (media_id). deleted_at 없음 → 비부분 인덱스.
CREATE TABLE gallery_photos (
    id         BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    album_id   BIGINT       NOT NULL REFERENCES gallery_albums (id),
    media_id   BIGINT       NOT NULL REFERENCES media (id),
    caption    VARCHAR(500),
    sort_order INTEGER      NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_gallery_photos_album_sort ON gallery_photos (album_id, sort_order);
CREATE INDEX idx_gallery_photos_media_id ON gallery_photos (media_id);
```

- [ ] **Step 2: 엔티티 작성**

`GalleryAlbum.java`:
```java
package com.elipair.church.domain.gallery;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 갤러리 앨범(스펙 §5.12). 수정가능 콘텐츠라 BaseEntity(감사·소프트삭제·낙관락)를 상속.
 * 스펙은 PATCH만(PUT 없음)이라 부분 수정 applyPatch만 둔다. 작성자 표시는 updated_by(설계 §1).
 */
@Entity
@Table(name = "gallery_albums")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GalleryAlbum extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private GalleryAlbum(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public static GalleryAlbum create(String title, String description) {
        return new GalleryAlbum(title, description);
    }

    /** PATCH 부분 수정 — null 인자는 미변경. */
    public void applyPatch(String title, String description) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
    }
}
```

`GalleryPhoto.java`:
```java
package com.elipair.church.domain.gallery;

import com.elipair.church.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 갤러리 사진(스펙 §5.12) — 앨범↔media 연결행. 경량(BaseTimeEntity, created_at만): soft delete/version 없음.
 * 연결 해제는 hard delete(media 원본은 라이브러리에 보존). album_id/media_id는 평문 Long FK(저결합).
 * caption은 MVP에서 미사용(추가 시 null) — 후속 이슈에서 편집 추가(설계 §10).
 */
@Entity
@Table(name = "gallery_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GalleryPhoto extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "album_id", nullable = false)
    private Long albumId;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    @Column(length = 500)
    private String caption;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    private GalleryPhoto(Long albumId, Long mediaId, Integer sortOrder) {
        this.albumId = albumId;
        this.mediaId = mediaId;
        this.sortOrder = sortOrder;
    }

    public static GalleryPhoto create(Long albumId, Long mediaId, Integer sortOrder) {
        return new GalleryPhoto(albumId, mediaId, sortOrder);
    }
}
```

- [ ] **Step 3: 프로젝션 인터페이스 4종 작성**

`GalleryAlbumRefRow.java`:
```java
package com.elipair.church.domain.gallery;

/** 앨범 본문 미디어 참조 추적용 프로젝션 — (id, title). */
public interface GalleryAlbumRefRow {
    Long getId();

    String getTitle();
}
```

`GalleryPhotoRefRow.java`:
```java
package com.elipair.church.domain.gallery;

/** 사진 FK 미디어 참조 추적용 프로젝션 — 소속 앨범 (id, title)로 표면화. */
public interface GalleryPhotoRefRow {
    Long getId();

    String getTitle();
}
```

`AlbumThumbnailRow.java`:
```java
package com.elipair.church.domain.gallery;

/** 앨범 목록 썸네일 배치 조회용 프로젝션 — (albumId, mediaId). 첫 사진(min sort_order). */
public interface AlbumThumbnailRow {
    Long getAlbumId();

    Long getMediaId();
}
```

`AlbumPhotoCountRow.java`:
```java
package com.elipair.church.domain.gallery;

/** 앨범 목록 사진수 배치 조회용 프로젝션 — (albumId, count). */
public interface AlbumPhotoCountRow {
    Long getAlbumId();

    Long getCount();
}
```

- [ ] **Step 4: 리포지토리 작성**

`GalleryAlbumRepository.java`:
```java
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
                    "select id as id, title as title from gallery_albums where deleted_at is null and description ~ :pattern",
            nativeQuery = true)
    List<GalleryAlbumRefRow> findReferencesByMedia(@Param("pattern") String pattern);
}
```

`GalleryPhotoRepository.java`:
```java
package com.elipair.church.domain.gallery;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GalleryPhotoRepository extends JpaRepository<GalleryPhoto, Long> {

    /** 앨범 상세용 — 앨범 내 사진 정렬(첫 사진=대표). 동률 시 id ASC(결정적). */
    List<GalleryPhoto> findByAlbumIdOrderBySortOrderAscIdAsc(Long albumId);

    /** 앨범 삭제 시 연결행 정리(설계 §6, FK 댕글링 차단). */
    void deleteByAlbumId(Long albumId);

    /** append 기준값 — 미존재 시 -1(→ 첫 사진 sort_order 0). */
    @Query("select coalesce(max(p.sortOrder), -1) from GalleryPhoto p where p.albumId = :albumId")
    int findMaxSortOrder(@Param("albumId") Long albumId);

    /** 목록 썸네일 배치 — 앨범당 첫 사진(min sort_order, 동률 id). PG DISTINCT ON. */
    @Query(
            value = "select distinct on (album_id) album_id as albumId, media_id as mediaId "
                    + "from gallery_photos where album_id in (:albumIds) order by album_id, sort_order, id",
            nativeQuery = true)
    List<AlbumThumbnailRow> findThumbnails(@Param("albumIds") Collection<Long> albumIds);

    /** 목록 사진수 배치. */
    @Query("select p.albumId as albumId, count(p) as count from GalleryPhoto p "
            + "where p.albumId in :albumIds group by p.albumId")
    List<AlbumPhotoCountRow> countByAlbumIds(@Param("albumIds") Collection<Long> albumIds);

    /**
     * 사진 FK 미디어 참조(스펙 §5.10 SQL) — 소속 앨범(id·title)으로 표면화. 미삭제 앨범만(a.deleted_at IS NULL).
     * 같은 앨범 내 중복 media는 DISTINCT로 1건.
     */
    @Query(
            value = "select distinct a.id as id, a.title as title from gallery_photos p "
                    + "join gallery_albums a on a.id = p.album_id "
                    + "where p.media_id = :mediaId and a.deleted_at is null",
            nativeQuery = true)
    List<GalleryPhotoRefRow> findReferencesByMediaId(@Param("mediaId") long mediaId);
}
```

- [ ] **Step 5: 리포지토리 테스트 작성**

`GalleryAlbumRepositoryTest.java`:
```java
package com.elipair.church.domain.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class GalleryAlbumRepositoryTest {

    @Autowired
    private GalleryAlbumRepository repository;

    @Test
    void save_populates_audit_and_version() {
        GalleryAlbum saved = repository.saveAndFlush(GalleryAlbum.create("부활절", "본문"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void findByIdAndDeletedAtIsNull_excludes_soft_deleted() {
        GalleryAlbum active = repository.saveAndFlush(GalleryAlbum.create("활성", "본문"));
        GalleryAlbum deleted = GalleryAlbum.create("삭제", "본문");
        deleted.softDelete();
        GalleryAlbum savedDeleted = repository.saveAndFlush(deleted);

        assertThat(repository.findByIdAndDeletedAtIsNull(active.getId())).isPresent();
        assertThat(repository.findByIdAndDeletedAtIsNull(savedDeleted.getId())).isEmpty();
    }

    @Test
    void findByIdForUpdate_returns_active_album() {
        GalleryAlbum saved = repository.saveAndFlush(GalleryAlbum.create("락대상", "본문"));
        assertThat(repository.findByIdForUpdate(saved.getId())).isPresent();
        GalleryAlbum deleted = GalleryAlbum.create("삭제", "본문");
        deleted.softDelete();
        GalleryAlbum savedDeleted = repository.saveAndFlush(deleted);
        assertThat(repository.findByIdForUpdate(savedDeleted.getId())).isEmpty();
    }

    @Test
    void findReferencesByMedia_is_boundary_safe() {
        repository.saveAndFlush(GalleryAlbum.create("42참조", "본문 ![](media:42) 끝"));
        repository.saveAndFlush(GalleryAlbum.create("420참조", "본문 ![](media:420) 끝"));

        List<GalleryAlbumRefRow> rows = repository.findReferencesByMedia("media:42($|[^0-9])");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTitle()).isEqualTo("42참조");
    }
}
```

`GalleryPhotoRepositoryTest.java`:
```java
package com.elipair.church.domain.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class GalleryPhotoRepositoryTest {

    @Autowired
    private GalleryAlbumRepository albumRepository;

    @Autowired
    private GalleryPhotoRepository photoRepository;

    private Long albumId;

    private Long newAlbum(String title) {
        return albumRepository.saveAndFlush(GalleryAlbum.create(title, "본문")).getId();
    }

    @Test
    void findMaxSortOrder_minus_one_when_empty_then_max() {
        albumId = newAlbum("A");
        assertThat(photoRepository.findMaxSortOrder(albumId)).isEqualTo(-1);
        photoRepository.saveAndFlush(GalleryPhoto.create(albumId, 1L, 0));
        photoRepository.saveAndFlush(GalleryPhoto.create(albumId, 2L, 1));
        assertThat(photoRepository.findMaxSortOrder(albumId)).isEqualTo(1);
    }

    @Test
    void findByAlbumId_orders_by_sort_then_id() {
        albumId = newAlbum("A");
        photoRepository.saveAndFlush(GalleryPhoto.create(albumId, 10L, 1));
        photoRepository.saveAndFlush(GalleryPhoto.create(albumId, 11L, 0));

        List<Long> mediaIds = photoRepository.findByAlbumIdOrderBySortOrderAscIdAsc(albumId).stream()
                .map(GalleryPhoto::getMediaId)
                .toList();
        assertThat(mediaIds).containsExactly(11L, 10L); // sort_order 0 먼저
    }

    @Test
    void deleteByAlbumId_removes_all_links() {
        albumId = newAlbum("A");
        photoRepository.saveAndFlush(GalleryPhoto.create(albumId, 1L, 0));
        photoRepository.saveAndFlush(GalleryPhoto.create(albumId, 2L, 1));

        photoRepository.deleteByAlbumId(albumId);

        assertThat(photoRepository.findByAlbumIdOrderBySortOrderAscIdAsc(albumId)).isEmpty();
    }

    @Test
    void thumbnails_returns_first_photo_per_album() {
        Long a1 = newAlbum("A1");
        Long a2 = newAlbum("A2");
        photoRepository.saveAndFlush(GalleryPhoto.create(a1, 100L, 1));
        photoRepository.saveAndFlush(GalleryPhoto.create(a1, 101L, 0)); // a1 대표
        photoRepository.saveAndFlush(GalleryPhoto.create(a2, 200L, 0)); // a2 대표

        List<AlbumThumbnailRow> rows = photoRepository.findThumbnails(List.of(a1, a2));

        assertThat(rows).hasSize(2);
        assertThat(rows).anySatisfy(r -> {
            assertThat(r.getAlbumId()).isEqualTo(a1);
            assertThat(r.getMediaId()).isEqualTo(101L);
        });
    }

    @Test
    void counts_returns_photo_count_per_album() {
        Long a1 = newAlbum("A1");
        photoRepository.saveAndFlush(GalleryPhoto.create(a1, 1L, 0));
        photoRepository.saveAndFlush(GalleryPhoto.create(a1, 2L, 1));

        List<AlbumPhotoCountRow> rows = photoRepository.countByAlbumIds(List.of(a1));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCount()).isEqualTo(2L);
    }

    @Test
    void findReferencesByMediaId_surfaces_album_and_excludes_deleted_album() {
        Long live = newAlbum("라이브앨범");
        photoRepository.saveAndFlush(GalleryPhoto.create(live, 42L, 0));
        photoRepository.saveAndFlush(GalleryPhoto.create(live, 42L, 1)); // 같은 media 중복 → DISTINCT 1건

        List<GalleryPhotoRefRow> rows = photoRepository.findReferencesByMediaId(42L);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTitle()).isEqualTo("라이브앨범");

        // 앨범이 soft-deleted면 조인 필터로 제외
        GalleryAlbum dead = GalleryAlbum.create("죽은앨범", "본문");
        dead.softDelete();
        Long deadId = albumRepository.saveAndFlush(dead).getId();
        photoRepository.saveAndFlush(GalleryPhoto.create(deadId, 77L, 0));
        assertThat(photoRepository.findReferencesByMediaId(77L)).isEmpty();
    }
}
```

- [ ] **Step 6: `MigrationIndexTest`에 부분 인덱스 검증 추가**

`MigrationIndexTest.java`의 `departments_sort_order_is_partial_on_active_rows` 테스트 아래(마지막 `}` 앞)에 추가:
```java
    @Test
    void gallery_albums_created_at_is_partial_on_active_rows() {
        assertThat(indexDef("idx_gallery_albums_created_at"))
                .as("V11 갤러리 앨범 목록 인덱스")
                .isNotNull()
                .contains("created_at")
                .contains("deleted_at IS NULL");
    }
```

- [ ] **Step 7: 테스트 실행 (리포지토리 슬라이스 + 마이그레이션)**

Run: `./gradlew test --tests 'com.elipair.church.domain.gallery.GalleryAlbumRepositoryTest' --tests 'com.elipair.church.domain.gallery.GalleryPhotoRepositoryTest' --tests 'com.elipair.church.MigrationIndexTest'`
Expected: PASS 전부. (만약 `idx_gallery_albums_created_at` 검증이 실패하면 V11 인덱스 정의의 `WHERE deleted_at IS NULL`을 확인.)

- [ ] **Step 8: 커밋**

```bash
git add src/main/resources/db/migration/V11__create_gallery.sql \
        src/main/java/com/elipair/church/domain/gallery/ \
        src/test/java/com/elipair/church/domain/gallery/GalleryAlbumRepositoryTest.java \
        src/test/java/com/elipair/church/domain/gallery/GalleryPhotoRepositoryTest.java \
        src/test/java/com/elipair/church/MigrationIndexTest.java
git commit -m "feat : 갤러리 엔티티·리포지토리·V11 마이그레이션 추가 #16"
```

---

## Task 3: 미디어 참조추적 Provider 2종

앨범 본문(LIKE) + 사진 FK(=) 참조 Provider. `MediaService`가 빈으로 주입받아 차단삭제 합집합에 더한다.

**Files:**
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryAlbumReferenceProvider.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryPhotoReferenceProvider.java`
- Test: `src/test/java/com/elipair/church/domain/gallery/GalleryReferenceProviderTest.java`

- [ ] **Step 1: 실패하는 Provider 테스트 작성**

`GalleryReferenceProviderTest.java`:
```java
package com.elipair.church.domain.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.common.ContentRef;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class GalleryReferenceProviderTest {

    @Autowired
    private GalleryAlbumRepository albumRepository;

    @Autowired
    private GalleryPhotoRepository photoRepository;

    private GalleryAlbumReferenceProvider albumProvider;
    private GalleryPhotoReferenceProvider photoProvider;

    @BeforeEach
    void init() {
        albumProvider = new GalleryAlbumReferenceProvider(albumRepository);
        photoProvider = new GalleryPhotoReferenceProvider(photoRepository);
    }

    @Test
    void album_provider_matches_body_boundary_safe() {
        albumRepository.saveAndFlush(GalleryAlbum.create("42참조", "본문 ![](media:42) 끝"));
        albumRepository.saveAndFlush(GalleryAlbum.create("420참조", "본문 ![](media:420) 끝"));

        List<ContentRef> refs = albumProvider.findReferences(42);
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).type()).isEqualTo("gallery_album");
        assertThat(refs.get(0).title()).isEqualTo("42참조");
    }

    @Test
    void album_provider_excludes_soft_deleted() {
        GalleryAlbum deleted = GalleryAlbum.create("삭제", "![](media:9)");
        deleted.softDelete();
        albumRepository.saveAndFlush(deleted);
        assertThat(albumProvider.findReferences(9)).isEmpty();
    }

    @Test
    void photo_provider_surfaces_owning_album() {
        Long albumId = albumRepository.saveAndFlush(GalleryAlbum.create("사진앨범", "본문")).getId();
        photoRepository.saveAndFlush(GalleryPhoto.create(albumId, 42L, 0));

        List<ContentRef> refs = photoProvider.findReferences(42);
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).type()).isEqualTo("gallery_photo");
        assertThat(refs.get(0).title()).isEqualTo("사진앨범");
    }

    @Test
    void photo_provider_empty_when_album_deleted() {
        GalleryAlbum dead = GalleryAlbum.create("죽은앨범", "본문");
        dead.softDelete();
        Long deadId = albumRepository.saveAndFlush(dead).getId();
        photoRepository.saveAndFlush(GalleryPhoto.create(deadId, 55L, 0));

        assertThat(photoProvider.findReferences(55)).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.gallery.GalleryReferenceProviderTest'`
Expected: 컴파일 실패 — Provider 클래스 없음.

- [ ] **Step 3: Provider 구현**

`GalleryAlbumReferenceProvider.java`:
```java
package com.elipair.church.domain.gallery;

import com.elipair.church.domain.media.MediaReferenceProvider;
import com.elipair.church.global.common.ContentRef;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 앨범 본문(description) media:{id} 참조 추적(스펙 §5.10 SPI). ContentRef.type="gallery_album".
 * soft-deleted 앨범 제외. 경계 안전: media:42가 media:420/421에 오탐되지 않는다.
 */
@Component
class GalleryAlbumReferenceProvider implements MediaReferenceProvider {

    private final GalleryAlbumRepository repository;

    GalleryAlbumReferenceProvider(GalleryAlbumRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ContentRef> findReferences(long mediaId) {
        String pattern = "media:" + mediaId + "($|[^0-9])";
        return repository.findReferencesByMedia(pattern).stream()
                .map(row -> new ContentRef("gallery_album", row.getId(), row.getTitle()))
                .toList();
    }
}
```

`GalleryPhotoReferenceProvider.java`:
```java
package com.elipair.church.domain.gallery;

import com.elipair.church.domain.media.MediaReferenceProvider;
import com.elipair.church.global.common.ContentRef;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 사진 FK(media_id) 참조 추적(스펙 §5.10 SPI) — 프로젝트 최초의 FK 기반 Provider. ContentRef.type="gallery_photo".
 * 사진 참조는 소속 앨범(id·title)으로 표면화하며, soft-deleted 앨범은 조인 필터로 제외(설계 §3).
 */
@Component
class GalleryPhotoReferenceProvider implements MediaReferenceProvider {

    private final GalleryPhotoRepository repository;

    GalleryPhotoReferenceProvider(GalleryPhotoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ContentRef> findReferences(long mediaId) {
        return repository.findReferencesByMediaId(mediaId).stream()
                .map(row -> new ContentRef("gallery_photo", row.getId(), row.getTitle()))
                .toList();
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.gallery.GalleryReferenceProviderTest'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/gallery/GalleryAlbumReferenceProvider.java \
        src/main/java/com/elipair/church/domain/gallery/GalleryPhotoReferenceProvider.java \
        src/test/java/com/elipair/church/domain/gallery/GalleryReferenceProviderTest.java
git commit -m "feat : 갤러리 미디어 참조추적 Provider 추가(앨범 본문·사진 FK) #16"
```

---

## Task 4: 요청·응답 DTO

레코드 5종. 자체 테스트는 없고(레코드+검증 애너테이션), Task 5(서비스)·Task 6(API)에서 실사용·검증한다.

**Files:**
- Create: `src/main/java/com/elipair/church/domain/gallery/dto/GalleryAlbumCreateRequest.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/dto/GalleryAlbumPatchRequest.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/dto/GalleryAlbumCardResponse.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/dto/GalleryAlbumDetailResponse.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/dto/GalleryPhotoResponse.java`

- [ ] **Step 1: DTO 작성**

`GalleryAlbumCreateRequest.java`:
```java
package com.elipair.church.domain.gallery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 앨범 생성(POST). @Size(max)는 V11 컬럼 길이/스펙 §5 최소검증 상한. */
public record GalleryAlbumCreateRequest(
        @NotBlank @Size(max = 200) String title, @Size(max = 50000) String description, List<Long> tagIds) {}
```

`GalleryAlbumPatchRequest.java`:
```java
package com.elipair.church.domain.gallery.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 앨범 부분 수정(PATCH). 전달된(비-null) 필드만 적용, tagIds null이면 태그 미변경. version 낙관락 필수. */
public record GalleryAlbumPatchRequest(
        @Size(max = 200) String title,
        @Size(max = 50000) String description,
        List<Long> tagIds,
        @NotNull Long version) {}
```

`GalleryPhotoResponse.java`:
```java
package com.elipair.church.domain.gallery.dto;

/** 앨범 상세에 임베드되는 사진 한 건(스펙 §5.12). caption은 MVP에서 null. */
public record GalleryPhotoResponse(Long id, Long mediaId, String caption, Integer sortOrder) {}
```

`GalleryAlbumCardResponse.java`:
```java
package com.elipair.church.domain.gallery.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 앨범 목록 카드(스펙 §5.12) — 본문 description 제외. thumbnailMediaId=첫 사진 media_id(없으면 null),
 * photoCount=앨범 내 사진 수. author=updated_by 표시명(탈퇴 마스킹).
 */
public record GalleryAlbumCardResponse(
        Long id,
        String title,
        Long thumbnailMediaId,
        long photoCount,
        LocalDateTime createdAt,
        List<TagResponse> tags,
        String author) {}
```

`GalleryAlbumDetailResponse.java`:
```java
package com.elipair.church.domain.gallery.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/** 앨범 상세(스펙 §5.12) — description·사진 목록·version(편집 재전송용)·작성자 포함. */
public record GalleryAlbumDetailResponse(
        Long id,
        String title,
        String description,
        List<TagResponse> tags,
        String author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version,
        List<GalleryPhotoResponse> photos) {}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/gallery/dto/
git commit -m "feat : 갤러리 요청·응답 DTO 추가 #16"
```

---

## Task 5: 서비스 (앨범·사진) + Specification

`GalleryAlbumService`(목록 배치·낙관락·태그·작성자·삭제 정리), `GalleryPhotoService`(앨범락·이미지검증·append·해제), `GalleryAlbumSpecifications`(태그 필터).

**Files:**
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryAlbumSpecifications.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryAlbumService.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryPhotoService.java`
- Test: `src/test/java/com/elipair/church/domain/gallery/GalleryAlbumServiceTest.java`
- Test: `src/test/java/com/elipair/church/domain/gallery/GalleryPhotoServiceTest.java`

- [ ] **Step 1: Specification 작성**

`GalleryAlbumSpecifications.java`:
```java
package com.elipair.church.domain.gallery;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 앨범 동적 필터(스펙 §5.12). 항상 미삭제만(deletedAt IS NULL). taggedIds는 서비스가 미리 해석해 넘긴 id 목록
 * (null=태그 필터 없음, 빈 리스트=해당 태그를 가진 앨범 없음 → 빈 결과).
 */
final class GalleryAlbumSpecifications {

    private GalleryAlbumSpecifications() {}

    static Specification<GalleryAlbum> filter(List<Long> taggedIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (taggedIds != null) {
                predicates.add(taggedIds.isEmpty() ? cb.disjunction() : root.get("id").in(taggedIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성 (앨범)**

`GalleryAlbumServiceTest.java`:
```java
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
```

- [ ] **Step 3: 실패하는 서비스 테스트 작성 (사진)**

`GalleryPhotoServiceTest.java`:
```java
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

        assertThatThrownBy(() -> service.addPhotos(1L, null, List.of(5L), 7L))
                .isInstanceOf(BusinessException.class);
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
```

- [ ] **Step 4: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.gallery.GalleryAlbumServiceTest' --tests 'com.elipair.church.domain.gallery.GalleryPhotoServiceTest'`
Expected: 컴파일 실패 — 서비스 클래스 없음.

- [ ] **Step 5: `GalleryAlbumService` 구현**

```java
package com.elipair.church.domain.gallery;

import com.elipair.church.domain.gallery.dto.GalleryAlbumCardResponse;
import com.elipair.church.domain.gallery.dto.GalleryAlbumCreateRequest;
import com.elipair.church.domain.gallery.dto.GalleryAlbumDetailResponse;
import com.elipair.church.domain.gallery.dto.GalleryAlbumPatchRequest;
import com.elipair.church.domain.gallery.dto.GalleryPhotoResponse;
import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
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

    public GalleryAlbumService(
            GalleryAlbumRepository repository,
            GalleryPhotoRepository photoRepository,
            ContentTagService contentTagService,
            AuthorDisplayService authorDisplayService) {
        this.repository = repository;
        this.photoRepository = photoRepository;
        this.contentTagService = contentTagService;
        this.authorDisplayService = authorDisplayService;
    }

    public Page<GalleryAlbumCardResponse> list(Long tagId, Pageable pageable) {
        List<Long> taggedIds = tagId == null ? null : contentTagService.resourceIdsWithTag(TYPE, tagId);
        Page<GalleryAlbum> page = repository.findAll(GalleryAlbumSpecifications.filter(taggedIds), pageable);

        List<Long> ids = page.map(GalleryAlbum::getId).getContent();
        var tagsMap = contentTagService.getTagsByResources(TYPE, ids);
        var authorMap = authorDisplayService.displayNames(
                page.map(GalleryAlbum::getUpdatedBy).getContent());
        Map<Long, Long> thumbMap = ids.isEmpty()
                ? Map.of()
                : photoRepository.findThumbnails(ids).stream()
                        .collect(Collectors.toMap(AlbumThumbnailRow::getAlbumId, AlbumThumbnailRow::getMediaId));
        Map<Long, Long> countMap = ids.isEmpty()
                ? Map.of()
                : photoRepository.countByAlbumIds(ids).stream()
                        .collect(Collectors.toMap(AlbumPhotoCountRow::getAlbumId, AlbumPhotoCountRow::getCount));

        return page.map(a -> new GalleryAlbumCardResponse(
                a.getId(),
                a.getTitle(),
                thumbMap.get(a.getId()),
                countMap.getOrDefault(a.getId(), 0L),
                a.getCreatedAt(),
                tagsMap.getOrDefault(a.getId(), List.of()),
                authorMap.getOrDefault(a.getUpdatedBy(), AuthorDisplayService.UNKNOWN)));
    }

    public GalleryAlbumDetailResponse get(Long id) {
        return detail(load(id));
    }

    @Transactional
    public GalleryAlbumDetailResponse create(GalleryAlbumCreateRequest req) {
        GalleryAlbum album = repository.save(GalleryAlbum.create(req.title(), req.description()));
        contentTagService.replaceLinks(TYPE, album.getId(), req.tagIds());
        return detail(album);
    }

    @Transactional
    public GalleryAlbumDetailResponse patch(Long id, GalleryAlbumPatchRequest req) {
        GalleryAlbum album = load(id);
        checkVersion(album, req.version());
        album.applyPatch(req.title(), req.description());
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
                .map(p -> new GalleryPhotoResponse(p.getId(), p.getMediaId(), p.getCaption(), p.getSortOrder()))
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
                photos);
    }
}
```

- [ ] **Step 6: `GalleryPhotoService` 구현**

```java
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
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.gallery.GalleryAlbumServiceTest' --tests 'com.elipair.church.domain.gallery.GalleryPhotoServiceTest'`
Expected: PASS 전부.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/gallery/GalleryAlbumSpecifications.java \
        src/main/java/com/elipair/church/domain/gallery/GalleryAlbumService.java \
        src/main/java/com/elipair/church/domain/gallery/GalleryPhotoService.java \
        src/test/java/com/elipair/church/domain/gallery/GalleryAlbumServiceTest.java \
        src/test/java/com/elipair/church/domain/gallery/GalleryPhotoServiceTest.java
git commit -m "feat : 갤러리 서비스 추가(낙관락·태그·작성자·앨범락·사진정리) #16"
```

---

## Task 6: 컨트롤러 (공개 조회 + 관리) + API 통합 테스트

`GalleryAlbumController`(회원 전용 조회), `AdminGalleryController`(관리). 풀 스택 통합 테스트로 인가 3분법·차단삭제 2경로·FK 안전·낙관락을 검증한다.

**Files:**
- Create: `src/main/java/com/elipair/church/domain/gallery/GalleryAlbumController.java`
- Create: `src/main/java/com/elipair/church/domain/gallery/AdminGalleryController.java`
- Test: `src/test/java/com/elipair/church/domain/gallery/GalleryApiTest.java`

- [ ] **Step 1: 컨트롤러 구현**

`GalleryAlbumController.java`:
```java
package com.elipair.church.domain.gallery;

import com.elipair.church.domain.gallery.dto.GalleryAlbumCardResponse;
import com.elipair.church.domain.gallery.dto.GalleryAlbumDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 갤러리 회원 전용 조회 API(스펙 §5.12). 경로 /api/gallery/**는 SecurityConfig가 GALLERY_VIEW를 강제하므로
 * 메서드 @PreAuthorize는 두지 않는다(공개 조회 도메인이 경로 규칙에 의존하는 관례와 동일).
 */
@RestController
public class GalleryAlbumController {

    private final GalleryAlbumService service;

    public GalleryAlbumController(GalleryAlbumService service) {
        this.service = service;
    }

    @GetMapping("/api/gallery/albums")
    public Page<GalleryAlbumCardResponse> list(
            @RequestParam(required = false) Long tagId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(tagId, pageable);
    }

    @GetMapping("/api/gallery/albums/{id}")
    public GalleryAlbumDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
```

`AdminGalleryController.java`:
```java
package com.elipair.church.domain.gallery;

import com.elipair.church.domain.gallery.dto.GalleryAlbumCreateRequest;
import com.elipair.church.domain.gallery.dto.GalleryAlbumDetailResponse;
import com.elipair.church.domain.gallery.dto.GalleryAlbumPatchRequest;
import com.elipair.church.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 갤러리 관리 API(스펙 §5.12). 전 메서드 GALLERY_WRITE. 사진 추가는 multipart(files/mediaIds 혼합). */
@RestController
@PreAuthorize("hasAuthority('GALLERY_WRITE')")
public class AdminGalleryController {

    private final GalleryAlbumService albumService;
    private final GalleryPhotoService photoService;

    public AdminGalleryController(GalleryAlbumService albumService, GalleryPhotoService photoService) {
        this.albumService = albumService;
        this.photoService = photoService;
    }

    @PostMapping("/api/admin/gallery/albums")
    public ResponseEntity<GalleryAlbumDetailResponse> create(@Valid @RequestBody GalleryAlbumCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(albumService.create(request));
    }

    @PatchMapping("/api/admin/gallery/albums/{id}")
    public GalleryAlbumDetailResponse patch(
            @PathVariable Long id, @Valid @RequestBody GalleryAlbumPatchRequest request) {
        return albumService.patch(id, request);
    }

    @DeleteMapping("/api/admin/gallery/albums/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        albumService.delete(id);
    }

    @PostMapping("/api/admin/gallery/albums/{id}/photos")
    public GalleryAlbumDetailResponse addPhotos(
            @PathVariable Long id,
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(required = false) List<Long> mediaIds,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return photoService.addPhotos(id, files, mediaIds, principal.id());
    }

    @DeleteMapping("/api/admin/gallery/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePhoto(@PathVariable Long photoId) {
        photoService.removePhoto(photoId);
    }
}
```

- [ ] **Step 2: 실패하는 API 통합 테스트 작성**

`GalleryApiTest.java`:
```java
package com.elipair.church.domain.gallery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GalleryApiTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1, 2, 3, 4, 5, 6, 7};

    @TempDir
    static Path uploadDir;

    @DynamicPropertySource
    static void fileProps(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> uploadDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private GalleryAlbumRepository albumRepository;

    @Autowired
    private GalleryPhotoRepository photoRepository;

    @Autowired
    private com.elipair.church.domain.media.MediaRepository mediaRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long adminId;

    @BeforeEach
    void seed() {
        Member admin =
                memberRepository.saveAndFlush(Member.create("01000000000", "관리목사", "{enc}", null, null, true, true));
        adminId = admin.getId();
    }

    @AfterEach
    void cleanup() {
        photoRepository.deleteAll();
        albumRepository.deleteAll();
        mediaRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    /** 관리자 토큰: 앨범 작성·읽기·미디어 삭제까지 한 토큰으로(통합 시나리오 편의). */
    private String adminToken() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(adminId, "uuid-admin", "관리자", 1000),
                        null,
                        List.of("GALLERY_WRITE", "GALLERY_VIEW", "MEDIA_MANAGE"));
    }

    private String token(String... authorities) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(adminId, "uuid-x", "사용자", 100), null, List.of(authorities));
    }

    private long createAlbum(String title, String description) throws Exception {
        String json = mockMvc.perform(post("/api/admin/gallery/albums")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"%s","tagIds":[]}
                                """.formatted(title, description)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long uploadMedia() throws Exception {
        String json = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG))
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // ---- 인가(스펙 §5.12 회원 전용 조회) ----

    @Test
    void list_anonymous_is_401() throws Exception {
        mockMvc.perform(get("/api/gallery/albums"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void list_plain_user_without_gallery_view_is_403() throws Exception {
        mockMvc.perform(get("/api/gallery/albums").header("Authorization", token("SERMON_WRITE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void list_member_with_gallery_view_is_200() throws Exception {
        createAlbum("부활절", "본문");
        mockMvc.perform(get("/api/gallery/albums").header("Authorization", token("GALLERY_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").doesNotExist());
    }

    @Test
    void create_without_gallery_write_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/gallery/albums")
                        .header("Authorization", token("GALLERY_VIEW"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","description":"y","tagIds":[]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ---- 앨범 CRUD ----

    @Test
    void create_returns_201_with_author_and_version() throws Exception {
        mockMvc.perform(post("/api/admin/gallery/albums")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"여름수련회","description":"본문 ![](media:1)","tagIds":[]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("여름수련회"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.author").value("관리목사"))
                .andExpect(jsonPath("$.photos").isArray());
    }

    @Test
    void patch_bumps_version_then_stale_is_409() throws Exception {
        long id = createAlbum("원본", "본문");
        mockMvc.perform(patch("/api/admin/gallery/albums/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"수정","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(patch("/api/admin/gallery/albums/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"또수정","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void delete_soft_deletes_then_detail_404() throws Exception {
        long id = createAlbum("삭제대상", "본문");
        mockMvc.perform(delete("/api/admin/gallery/albums/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/gallery/albums/" + id).header("Authorization", token("GALLERY_VIEW")))
                .andExpect(status().isNotFound());
    }

    // ---- 사진 추가/해제 (multipart 혼합) ----

    @Test
    void add_photos_via_existing_media_and_upload_then_thumbnail_is_first() throws Exception {
        long albumId = createAlbum("앨범", "본문");
        long existingMedia = uploadMedia();

        // mediaIds(기존) + files(신규 업로드) 혼합 → mediaIds 먼저(sort 0), 업로드 뒤(sort 1)
        mockMvc.perform(multipart("/api/admin/gallery/albums/" + albumId + "/photos")
                        .file(new MockMultipartFile("files", "new.jpg", "image/jpeg", JPEG))
                        .param("mediaIds", String.valueOf(existingMedia))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andExpect(jsonPath("$.photos[0].mediaId").value((int) existingMedia))
                .andExpect(jsonPath("$.photos[0].sortOrder").value(0))
                .andExpect(jsonPath("$.photos[1].sortOrder").value(1));

        // 카드 썸네일 = 첫 사진(sort 0) media
        mockMvc.perform(get("/api/gallery/albums").header("Authorization", token("GALLERY_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].thumbnailMediaId").value((int) existingMedia))
                .andExpect(jsonPath("$.content[0].photoCount").value(2));
    }

    @Test
    void add_photos_rejects_non_image_existing_media_400() throws Exception {
        long albumId = createAlbum("앨범", "본문");
        // PDF media 직접 생성(라이브러리엔 PDF도 존재) → 갤러리 추가는 이미지만 허용
        long pdfId = mediaRepository
                .saveAndFlush(
                        com.elipair.church.domain.media.Media.create("b.pdf", "p/b.pdf", "application/pdf", 1L, adminId))
                .getId();

        mockMvc.perform(multipart("/api/admin/gallery/albums/" + albumId + "/photos")
                        .param("mediaIds", String.valueOf(pdfId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void remove_photo_unlinks_but_keeps_media() throws Exception {
        long albumId = createAlbum("앨범", "본문");
        long media = uploadMedia();
        String json = mockMvc.perform(multipart("/api/admin/gallery/albums/" + albumId + "/photos")
                        .param("mediaIds", String.valueOf(media))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        int photoId = JsonPath.read(json, "$.photos[0].id");

        mockMvc.perform(delete("/api/admin/gallery/photos/" + photoId).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        // media 원본은 보존(서빙 가능)
        mockMvc.perform(get("/api/media/" + media)).andExpect(status().isOk());
    }

    // ---- 미디어 차단삭제 2경로 + FK 안전(설계 Critical) ----

    @Test
    void media_delete_blocked_by_photo_fk_409() throws Exception {
        long albumId = createAlbum("앨범", "본문");
        long media = uploadMedia();
        mockMvc.perform(multipart("/api/admin/gallery/albums/" + albumId + "/photos")
                        .param("mediaIds", String.valueOf(media))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/media/" + media).header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_IN_USE"))
                .andExpect(jsonPath("$.references[0].type").value("gallery_photo"));
    }

    @Test
    void media_delete_blocked_by_album_body_409() throws Exception {
        long media = uploadMedia();
        createAlbum("본문참조앨범", "사진 ![](media:" + media + ") 끝");

        mockMvc.perform(delete("/api/admin/media/" + media).header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_IN_USE"))
                .andExpect(jsonPath("$.references[0].type").value("gallery_album"));
    }

    @Test
    void media_deletable_after_album_deleted_no_fk_violation() throws Exception {
        long albumId = createAlbum("앨범", "본문");
        long media = uploadMedia();
        mockMvc.perform(multipart("/api/admin/gallery/albums/" + albumId + "/photos")
                        .param("mediaIds", String.valueOf(media))
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk());

        // 앨범 삭제 → 사진 행 hard delete → media는 참조 0 → 삭제 성공(FK 위반 없음)
        mockMvc.perform(delete("/api/admin/gallery/albums/" + albumId).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/media/" + media).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
    }

    // ---- 태그 필터 ----

    @Test
    void list_filters_by_tag() throws Exception {
        // 태그 없는 앨범만 만들고 tagId 미존재로 빈 결과 확인(글로벌 태그 풀은 tag 도메인 검증에 위임)
        createAlbum("무태그", "본문");
        mockMvc.perform(get("/api/gallery/albums")
                        .param("tagId", "999999")
                        .header("Authorization", token("GALLERY_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.gallery.GalleryApiTest'`
Expected: 컨트롤러 없으면 컴파일 실패 → 컨트롤러 추가 후 재실행. (Step 1에서 컨트롤러를 이미 만들었으면 테스트 메서드 단위 실패에서 시작.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.gallery.GalleryApiTest'`
Expected: PASS 전부. 실패 시 점검 포인트:
- `list_plain_user_without_gallery_view_is_403`: SecurityConfig 경로 규칙(`/api/gallery/** → GALLERY_VIEW`) 확인.
- `media_delete_blocked_by_photo_fk_409`: `GalleryPhotoReferenceProvider`가 빈으로 등록됐는지(@Component) 확인.
- `media_deletable_after_album_deleted...`: `delete`가 `photoRepository.deleteByAlbumId` 호출하는지 확인.

- [ ] **Step 5: 전체 테스트 실행(회귀 확인)**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (전 도메인 회귀 없음).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/gallery/GalleryAlbumController.java \
        src/main/java/com/elipair/church/domain/gallery/AdminGalleryController.java \
        src/test/java/com/elipair/church/domain/gallery/GalleryApiTest.java
git commit -m "feat : 갤러리 공개·관리 API 추가 #16"
```

---

## Task 7: 설계·계획 문서 커밋

`docs/superpowers/specs/2026-06-07-gallery-domain-design.md`(작성·리뷰 반영 완료)와 본 계획서를 #15 관례("설계·계획서 추가")대로 함께 커밋한다.

> 사용자 지시("commit only when asked")에 따라, 이 Task는 사용자가 커밋을 승인했을 때 수행한다.

- [ ] **Step 1: 문서 커밋**

```bash
git add docs/superpowers/specs/2026-06-07-gallery-domain-design.md \
        docs/superpowers/plans/2026-06-07-gallery-domain.md
git commit -m "docs : 갤러리 도메인 설계·계획서 추가 #16"
```

---

## 자체 검토 체크리스트 (구현 중 확인)

- [ ] **인가 3분법**: `/api/gallery/**`=GALLERY_VIEW(회원 전용), `/api/admin/gallery/**`=GALLERY_WRITE. USER·비로그인 차단 테스트 통과.
- [ ] **차단삭제 2경로**: 앨범 본문(LIKE)·사진 FK(=) 둘 다 references에 표면화, 409 MEDIA_IN_USE.
- [ ] **Critical FK 안전**: 앨범 삭제 → 사진 행 hard delete → 그 media 삭제 시 FK 위반 없음.
- [ ] **Major 고아 파일**: 비이미지 업로드는 저장 전 거부(디스크 파일 미생성).
- [ ] **Major sort_order 경합**: addPhotos가 `findByIdForUpdate` 비관락으로 직렬화, append는 max+1.
- [ ] **낙관락**: PATCH version 불일치 409, flush로 응답 version 정합, tag-only는 version 유지.
- [ ] **작성자 표시**: updated_by 기준, 탈퇴 시 "(탈퇴한 사용자)".
- [ ] **목록 카드**: description 제외, thumbnailMediaId(첫 사진)·photoCount 포함, N+1 배치.
- [ ] **부분 인덱스 회귀**: `MigrationIndexTest`에 `idx_gallery_albums_created_at` 1건 추가.
- [ ] **스펙 일치**: 앨범은 PATCH만(PUT 없음), 사진은 추가·해제만(순서/캡션 편집은 비범위).
