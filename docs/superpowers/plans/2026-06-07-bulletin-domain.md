# 주보(Bulletin) 도메인 구현 계획 — #17

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 날짜별 주보 PDF를 공개 전시하는 `bulletin` 도메인을 스펙 §5.13대로 구현한다(공개 조회 + `BULLETIN_WRITE` 관리 CRUD + 미디어 차단삭제 연동).

**Architecture:** notice(공개 CRUD·작성자 표시·낙관락) + 갤러리 사진(media FK 참조추적)의 합성. `Bulletin`은 `BaseEntity` 상속, `media_id`는 평문 `Long` FK(nullable + `ON DELETE SET NULL`, 설계 §2.1). PDF는 중앙 미디어 라이브러리 재사용 — 신규 업로드(`uploadPdf`) 또는 기존 `mediaId` 연결(`requirePdf`). 모든 입력 검증은 파일 쓰기(`uploadPdf`)보다 먼저 수행해 고아 파일을 막는다(설계 §6.1).

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Data JPA, PostgreSQL 16(Flyway V12), Spring Security(@PreAuthorize), JUnit 5 + Mockito + Testcontainers.

**설계 문서:** `docs/superpowers/specs/2026-06-07-bulletin-domain-design.md`

**커밋 컨벤션:** `<type> : <설명> #17` (콜론 앞 공백, 한글). Co-Authored-By 금지.

---

## 파일 구조

신규 (`src/main/java/com/elipair/church/domain/bulletin/`):
- `Bulletin.java` — 엔티티(`BaseEntity` 상속, title·service_date·media_id)
- `BulletinRepository.java` — `findByDeletedAtIsNull`·`findByIdAndDeletedAtIsNull`·`findReferencesByMediaId`
- `BulletinRefRow.java` — 참조추적 프로젝션(id, title)
- `BulletinReferenceProvider.java` — `MediaReferenceProvider` 구현(FK 기반)
- `BulletinService.java` — list/get/create/patch/delete + §6.1 검증
- `dto/BulletinCardResponse.java`, `dto/BulletinDetailResponse.java`
- `BulletinController.java`(공개), `AdminBulletinController.java`(관리, multipart)

신규 (`src/main/resources/db/migration/`):
- `V12__create_bulletins.sql`

수정:
- `src/main/java/com/elipair/church/domain/media/MediaService.java` — `uploadPdf` / `requirePdf` 추가
- `src/test/java/com/elipair/church/MigrationIndexTest.java` — 부분인덱스 + FK 액션 검증 2건 추가
- `src/test/java/com/elipair/church/domain/media/MediaServiceTest.java` — PDF 메서드 테스트 추가

신규 테스트:
- `src/test/java/com/elipair/church/domain/bulletin/BulletinReferenceProviderTest.java`
- `src/test/java/com/elipair/church/domain/bulletin/BulletinServiceTest.java`
- `src/test/java/com/elipair/church/domain/bulletin/BulletinApiTest.java`

### 컨트롤러 입력 방식 메모 (왜 `@RequestParam` + 서비스 검증인가)
멀티파트 스칼라는 전부 `@RequestParam(required = false)` + 자연 타입으로 받고, 필수성·공백·길이·XOR·non-empty는 **서비스에서** `BusinessException(INVALID_INPUT_VALUE)`로 검증한다. 이유:
1. 갤러리(`AdminGalleryController.addPhotos`)·미디어 컨트롤러가 이미 `@RequestParam` 자연타입 방식이라 일관.
2. `GlobalExceptionHandler`에 `MissingServletRequestParameterException` 핸들러가 없어 `required = true`로 누락 시 catch-all `Exception` → **500**이 된다. `required = false` + 서비스 검증이면 누락도 null → 서비스 400으로 일관된다.
3. 검증을 `uploadPdf`보다 먼저 두어야 고아 파일이 안 생긴다(설계 §6.1) — 선언적 `@Valid`로는 이 순서를 강제하기 까다롭다.

> 악의적 malformed 바인딩(예: `serviceDate=garbage`, `version=abc`)은 `MethodArgumentTypeMismatchException` → 500 — 이는 기존 코드베이스(미디어 `from`/`to`, 갤러리 `mediaIds`)도 동일하게 갖는 기수용 동작이라 맞춘다(스코프 밖).

---

## Task 1: V12 마이그레이션 + 스키마 회귀 테스트

**Files:**
- Create: `src/main/resources/db/migration/V12__create_bulletins.sql`
- Modify: `src/test/java/com/elipair/church/MigrationIndexTest.java`

`MigrationIndexTest`는 Flyway를 실제로 켜서(`spring.flyway.enabled=true`, `ddl-auto=validate`) 마이그레이션을 적용하고 `pg_indexes`·`information_schema`를 조회한다. `media_id`는 평문 `Long`이라 **JPA가 FK를 만들지 않으므로** ON DELETE SET NULL은 이 Flyway-on 테스트에서만 검증 가능하다(설계 §8).

- [ ] **Step 1: 실패하는 회귀 테스트 2건 추가**

`MigrationIndexTest.java`의 마지막 `@Test`(`gallery_albums_created_at_is_partial_on_active_rows`) 뒤, 클래스 닫는 `}` 앞에 추가:

```java
    @Test
    void bulletins_service_date_is_partial_on_active_rows() {
        assertThat(indexDef("idx_bulletins_service_date"))
                .as("V12 주보 목록 인덱스")
                .isNotNull()
                .contains("service_date")
                .contains("deleted_at IS NULL");
    }

    @Test
    void bulletins_media_id_fk_is_on_delete_set_null() {
        List<?> rules = em.createNativeQuery(
                        "select rc.delete_rule from information_schema.referential_constraints rc "
                                + "join information_schema.key_column_usage kcu "
                                + "  on kcu.constraint_name = rc.constraint_name "
                                + "  and kcu.constraint_schema = rc.constraint_schema "
                                + "where kcu.table_name = 'bulletins' and kcu.column_name = 'media_id'")
                .getResultList();
        assertThat(rules).as("V12 주보 media_id FK 존재").hasSize(1);
        assertThat((String) rules.get(0)).as("ON DELETE SET NULL").isEqualTo("SET NULL");
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.MigrationIndexTest'`
Expected: FAIL — `idx_bulletins_service_date`가 null(인덱스 없음), FK 조회 결과 비어 `hasSize(1)` 실패.

- [ ] **Step 3: V12 마이그레이션 작성**

`src/main/resources/db/migration/V12__create_bulletins.sql`:

```sql
-- 주보(스펙 §5.13). BaseEntity(감사·소프트삭제·낙관락) 상속 — V7~V11 관례. V11=gallery 점유 → V12.
-- media_id는 nullable + ON DELETE SET NULL: 활성 주보는 차단형 삭제로 보호되고, soft-deleted 주보만
--   media 하드삭제 시 자동 null화돼 FK 댕글링을 막는다(설계 §2.1). 활성 비-null은 앱 레이어가 보장.
CREATE TABLE bulletins (
    id           BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    service_date DATE         NOT NULL,
    media_id     BIGINT       REFERENCES media (id) ON DELETE SET NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP,
    created_by   BIGINT       REFERENCES members (id),
    updated_by   BIGINT       REFERENCES members (id),
    deleted_at   TIMESTAMP,
    version      BIGINT       NOT NULL DEFAULT 0
);

-- 목록 정렬 = service_date DESC, 미삭제만(스펙 §6 부분 인덱스).
CREATE INDEX idx_bulletins_service_date ON bulletins (service_date DESC) WHERE deleted_at IS NULL;
-- media 차단삭제 FK 검색용(deleted_at 조건은 Provider 쿼리가 담당 → 비부분 인덱스).
CREATE INDEX idx_bulletins_media_id ON bulletins (media_id);
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.MigrationIndexTest'`
Expected: PASS (기존 6건 + 신규 2건 모두 통과).

- [ ] **Step 5: 커밋**

```bash
git add src/main/resources/db/migration/V12__create_bulletins.sql src/test/java/com/elipair/church/MigrationIndexTest.java
git commit -m "feat : 주보 V12 마이그레이션·스키마 회귀 테스트 추가 #17"
```

---

## Task 2: 영속 계층 — 엔티티·리포지토리·참조 Provider

**Files:**
- Create: `src/main/java/com/elipair/church/domain/bulletin/Bulletin.java`
- Create: `src/main/java/com/elipair/church/domain/bulletin/BulletinRepository.java`
- Create: `src/main/java/com/elipair/church/domain/bulletin/BulletinRefRow.java`
- Create: `src/main/java/com/elipair/church/domain/bulletin/BulletinReferenceProvider.java`
- Test: `src/test/java/com/elipair/church/domain/bulletin/BulletinReferenceProviderTest.java`

- [ ] **Step 1: 실패하는 Provider 테스트 작성**

`src/test/java/com/elipair/church/domain/bulletin/BulletinReferenceProviderTest.java`:

```java
package com.elipair.church.domain.bulletin;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.common.ContentRef;
import com.elipair.church.global.config.JpaConfig;
import java.time.LocalDate;
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
class BulletinReferenceProviderTest {

    @Autowired
    private BulletinRepository repository;

    private BulletinReferenceProvider provider;

    @BeforeEach
    void init() {
        provider = new BulletinReferenceProvider(repository);
    }

    @Test
    void provider_surfaces_active_bulletin_referencing_media() {
        repository.saveAndFlush(Bulletin.create("2026-06-01 주보", LocalDate.of(2026, 6, 1), 42L));

        List<ContentRef> refs = provider.findReferences(42);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).type()).isEqualTo("bulletin");
        assertThat(refs.get(0).title()).isEqualTo("2026-06-01 주보");
    }

    @Test
    void provider_excludes_soft_deleted_bulletin() {
        Bulletin dead = Bulletin.create("삭제된 주보", LocalDate.of(2026, 5, 1), 9L);
        dead.softDelete();
        repository.saveAndFlush(dead);

        assertThat(provider.findReferences(9)).isEmpty();
    }

    @Test
    void provider_empty_when_no_match() {
        repository.saveAndFlush(Bulletin.create("다른 미디어", LocalDate.of(2026, 4, 1), 1L));

        assertThat(provider.findReferences(999)).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인 (컴파일 에러)**

Run: `./gradlew test --tests 'com.elipair.church.domain.bulletin.BulletinReferenceProviderTest'`
Expected: FAIL — `Bulletin`/`BulletinRepository`/`BulletinReferenceProvider` 미존재로 컴파일 실패.

- [ ] **Step 3: 엔티티 작성**

`src/main/java/com/elipair/church/domain/bulletin/Bulletin.java`:

```java
package com.elipair.church.domain.bulletin;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주보(스펙 §5.13). 수정가능 콘텐츠라 BaseEntity(감사·소프트삭제·낙관락)를 상속.
 * media_id는 평문 Long FK(저결합, 갤러리 관례). 컬럼은 nullable + ON DELETE SET NULL(설계 §2.1 —
 * soft-deleted 주보의 FK 댕글링 방지)이지만, 활성 주보의 비-null은 서비스(생성 시 file XOR mediaId 필수)가 보장한다.
 * created_by/updated_by는 AuditorAware가 자동 주입(서비스 수동 세팅 안 함).
 */
@Entity
@Table(name = "bulletins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bulletin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(name = "media_id")
    private Long mediaId;

    private Bulletin(String title, LocalDate serviceDate, Long mediaId) {
        this.title = title;
        this.serviceDate = serviceDate;
        this.mediaId = mediaId;
    }

    public static Bulletin create(String title, LocalDate serviceDate, Long mediaId) {
        return new Bulletin(title, serviceDate, mediaId);
    }

    /** PATCH 부분 수정 — null 인자는 미변경. mediaId는 새 PDF 해소 결과(없으면 null=기존 유지). */
    public void applyPatch(String title, LocalDate serviceDate, Long mediaId) {
        if (title != null) {
            this.title = title;
        }
        if (serviceDate != null) {
            this.serviceDate = serviceDate;
        }
        if (mediaId != null) {
            this.mediaId = mediaId;
        }
    }
}
```

- [ ] **Step 4: 프로젝션·리포지토리 작성**

`src/main/java/com/elipair/church/domain/bulletin/BulletinRefRow.java`:

```java
package com.elipair.church.domain.bulletin;

/** 주보 FK 미디어 참조 추적용 프로젝션 (id, title). */
public interface BulletinRefRow {
    Long getId();

    String getTitle();
}
```

`src/main/java/com/elipair/church/domain/bulletin/BulletinRepository.java`:

```java
package com.elipair.church.domain.bulletin;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BulletinRepository extends JpaRepository<Bulletin, Long> {

    /** 공개 목록 — 미삭제만. 정렬은 Pageable(service_date DESC 기본). */
    Page<Bulletin> findByDeletedAtIsNull(Pageable pageable);

    Optional<Bulletin> findByIdAndDeletedAtIsNull(Long id);

    /** media_id FK 참조(스펙 §5.10) — 활성 주보만(deleted_at IS NULL). */
    @Query(
            value = "select id as id, title as title from bulletins where media_id = :mediaId and deleted_at is null",
            nativeQuery = true)
    List<BulletinRefRow> findReferencesByMediaId(@Param("mediaId") long mediaId);
}
```

- [ ] **Step 5: 참조 Provider 작성**

`src/main/java/com/elipair/church/domain/bulletin/BulletinReferenceProvider.java`:

```java
package com.elipair.church.domain.bulletin;

import com.elipair.church.domain.media.MediaReferenceProvider;
import com.elipair.church.global.common.ContentRef;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 주보 PDF FK(media_id) 참조 추적(스펙 §5.10 SPI). ContentRef.type="bulletin".
 * 활성 주보만 참조로 보고(soft-deleted 제외) media 차단삭제에 합류 — 갤러리 사진 Provider와 동일 구조.
 */
@Component
class BulletinReferenceProvider implements MediaReferenceProvider {

    private final BulletinRepository repository;

    BulletinReferenceProvider(BulletinRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ContentRef> findReferences(long mediaId) {
        return repository.findReferencesByMediaId(mediaId).stream()
                .map(row -> new ContentRef("bulletin", row.getId(), row.getTitle()))
                .toList();
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.bulletin.BulletinReferenceProviderTest'`
Expected: PASS (3건).

- [ ] **Step 7: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/bulletin/ src/test/java/com/elipair/church/domain/bulletin/BulletinReferenceProviderTest.java
git commit -m "feat : 주보 엔티티·리포지토리·미디어 참조 Provider 추가 #17"
```

---

## Task 3: MediaService PDF 메서드 추가

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/media/MediaService.java`
- Test: `src/test/java/com/elipair/church/domain/media/MediaServiceTest.java`

`uploadPdf`/`requirePdf`는 기존 `uploadImage`/`requireImages`의 PDF 대칭이다. `detectMime`·`persist`·`findById`(전부 private)를 재사용한다.

- [ ] **Step 1: 실패하는 테스트 추가**

`MediaServiceTest.java`의 마지막 `@Test`(`requireImages_rejects_null_element_before_db_lookup`) 뒤, 클래스 닫는 `}` 앞에 추가. (`PDF`·`JPEG` 상수는 같은 클래스에 이미 정의돼 있어 재사용한다.)

```java
    @Test
    void uploadPdf_stores_pdf_and_returns_response() {
        MockMultipartFile file = new MockMultipartFile("file", "b.pdf", "application/octet-stream", PDF);
        when(fileStorage.store(file)).thenReturn("2026/06/b.pdf");
        when(repository.save(any(Media.class))).thenAnswer(inv -> inv.getArgument(0));

        MediaResponse res = service(List.of()).uploadPdf(file, 7L);

        assertThat(res.mimeType()).isEqualTo("application/pdf");
        verify(repository).save(any(Media.class));
    }

    @Test
    void uploadPdf_rejects_image_before_storing() {
        MockMultipartFile file = new MockMultipartFile("file", "p.jpg", "application/pdf", JPEG);

        assertThatThrownBy(() -> service(List.of()).uploadPdf(file, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(fileStorage); // 저장 전 거부 → 고아 파일 없음
        verify(repository, never()).save(any());
    }

    @Test
    void requirePdf_passes_when_media_is_pdf() {
        when(repository.findById(3L)).thenReturn(Optional.of(Media.create("b.pdf", "p", "application/pdf", 1L, 1L)));

        service(List.of()).requirePdf(3L); // 예외 없음
    }

    @Test
    void requirePdf_throws_404_when_missing() {
        when(repository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(List.of()).requirePdf(3L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void requirePdf_throws_400_when_media_is_image() {
        when(repository.findById(3L)).thenReturn(Optional.of(Media.create("a.jpg", "p", "image/jpeg", 1L, 1L)));

        assertThatThrownBy(() -> service(List.of()).requirePdf(3L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void requirePdf_throws_400_when_null() {
        assertThatThrownBy(() -> service(List.of()).requirePdf(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.media.MediaServiceTest'`
Expected: FAIL — `uploadPdf`/`requirePdf` 미존재로 컴파일 실패.

- [ ] **Step 3: MediaService에 메서드 추가**

`MediaService.java`에서 `uploadImage` 메서드(저장 전 이미지 검증) 바로 뒤에 추가:

```java
    /** 주보 전용 — 저장 전에 PDF 여부를 확정해 비PDF는 파일을 쓰지 않고 거부(고아 파일 차단, 설계 §6.1). */
    @Transactional
    public MediaResponse uploadPdf(MultipartFile file, Long uploaderId) {
        String mimeType = detectMime(file);
        if (!mimeType.equals("application/pdf")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "PDF 파일만 업로드할 수 있습니다");
        }
        return persist(file, mimeType, uploaderId);
    }

    /** 기존 라이브러리에서 고른 mediaId가 존재하고 PDF인지 검증(설계 §6.1). */
    public void requirePdf(Long mediaId) {
        if (mediaId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "mediaId는 필수입니다");
        }
        Media media = findById(mediaId); // 미존재 시 RESOURCE_NOT_FOUND
        if (!media.getMimeType().equals("application/pdf")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "PDF 미디어만 연결할 수 있습니다");
        }
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.media.MediaServiceTest'`
Expected: PASS (기존 + 신규 6건).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/media/MediaService.java src/test/java/com/elipair/church/domain/media/MediaServiceTest.java
git commit -m "feat : 미디어 PDF 전용 업로드·존재검증 메서드 추가 #17"
```

---

## Task 4: DTO + BulletinService

**Files:**
- Create: `src/main/java/com/elipair/church/domain/bulletin/dto/BulletinCardResponse.java`
- Create: `src/main/java/com/elipair/church/domain/bulletin/dto/BulletinDetailResponse.java`
- Create: `src/main/java/com/elipair/church/domain/bulletin/BulletinService.java`
- Test: `src/test/java/com/elipair/church/domain/bulletin/BulletinServiceTest.java`

- [ ] **Step 1: DTO 작성** (테스트가 참조하므로 먼저 생성)

`src/main/java/com/elipair/church/domain/bulletin/dto/BulletinCardResponse.java`:

```java
package com.elipair.church.domain.bulletin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 주보 목록 카드(스펙 §5.13). 본문 없음. author = updated_by 표시 이름. */
public record BulletinCardResponse(
        Long id, String title, LocalDate serviceDate, Long mediaId, LocalDateTime createdAt, String author) {}
```

`src/main/java/com/elipair/church/domain/bulletin/dto/BulletinDetailResponse.java`:

```java
package com.elipair.church.domain.bulletin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 주보 상세(스펙 §5.13). mediaId로 PDF 접근(프론트가 /api/media/{id}). version은 편집 재전송용. */
public record BulletinDetailResponse(
        Long id,
        String title,
        LocalDate serviceDate,
        Long mediaId,
        String author,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version) {}
```

- [ ] **Step 2: 실패하는 서비스 테스트 작성**

`src/test/java/com/elipair/church/domain/bulletin/BulletinServiceTest.java`:

```java
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
        when(repository.save(any(Bulletin.class))).thenReturn(mockBulletin(0L));
        MultipartFile file = pdfFile();

        service.create("2026 부활절 주보", DATE, file, null, 1L);

        verify(mediaService).uploadPdf(file, 1L);
        verify(repository).save(any(Bulletin.class));
    }

    @Test
    void create_with_mediaId_requires_pdf_and_saves() {
        when(repository.save(any(Bulletin.class))).thenReturn(mockBulletin(0L));

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
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(mockBulletin(0L)));

        service.patch(10L, 0L, "수정 제목", DATE, null, null, 1L);

        verify(mediaService, never()).uploadPdf(any(), any());
        verify(mediaService, never()).requirePdf(any());
        verify(repository).flush();
    }

    @Test
    void patch_replaces_pdf_with_file() {
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(mockBulletin(0L)));
        when(mediaService.uploadPdf(any(), any())).thenReturn(pdfMedia(77L));
        MultipartFile file = pdfFile();

        service.patch(10L, 0L, null, null, file, null, 1L);

        verify(mediaService).uploadPdf(file, 1L);
        verify(repository).flush();
    }

    @Test
    void patch_stale_version_throws_409_and_never_uploads() {
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(mockBulletin(3L)));

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
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.bulletin.BulletinServiceTest'`
Expected: FAIL — `BulletinService` 미존재로 컴파일 실패.

- [ ] **Step 4: 서비스 작성**

`src/main/java/com/elipair/church/domain/bulletin/BulletinService.java`:

```java
package com.elipair.church.domain.bulletin;

import com.elipair.church.domain.bulletin.dto.BulletinCardResponse;
import com.elipair.church.domain.bulletin.dto.BulletinDetailResponse;
import com.elipair.church.domain.media.MediaService;
import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주보 서비스(스펙 §5.13). notice 답습(공개 CRUD·작성자 표시·낙관락) + 갤러리식 PDF 연결(MediaService).
 * 모든 입력 검증·낙관락 확인은 uploadPdf(디스크 쓰기)보다 먼저 수행한다 — 검증/충돌 실패가 고아 파일을 남기지 않도록(설계 §6.1).
 */
@Service
@Transactional(readOnly = true)
public class BulletinService {

    private static final int TITLE_MAX = 200;

    private final BulletinRepository repository;
    private final MediaService mediaService;
    private final AuthorDisplayService authorDisplayService;

    public BulletinService(
            BulletinRepository repository, MediaService mediaService, AuthorDisplayService authorDisplayService) {
        this.repository = repository;
        this.mediaService = mediaService;
        this.authorDisplayService = authorDisplayService;
    }

    public Page<BulletinCardResponse> list(Pageable pageable) {
        Page<Bulletin> page = repository.findByDeletedAtIsNull(pageable);
        Map<Long, String> authorMap =
                authorDisplayService.displayNames(page.map(Bulletin::getUpdatedBy).getContent());
        return page.map(b -> new BulletinCardResponse(
                b.getId(),
                b.getTitle(),
                b.getServiceDate(),
                b.getMediaId(),
                b.getCreatedAt(),
                authorMap.getOrDefault(b.getUpdatedBy(), AuthorDisplayService.UNKNOWN)));
    }

    public BulletinDetailResponse get(Long id) {
        return detail(load(id));
    }

    @Transactional
    public BulletinDetailResponse create(
            String title, LocalDate serviceDate, MultipartFile file, Long mediaId, Long uploaderId) {
        validateTitle(title, true);
        if (serviceDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "serviceDate는 필수입니다");
        }
        long resolvedMediaId = resolveMedia(file, mediaId, true, uploaderId);
        return detail(repository.save(Bulletin.create(title, serviceDate, resolvedMediaId)));
    }

    @Transactional
    public BulletinDetailResponse patch(
            Long id,
            Long version,
            String title,
            LocalDate serviceDate,
            MultipartFile file,
            Long mediaId,
            Long uploaderId) {
        if (version == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "version은 필수입니다");
        }
        validateTitle(title, false);
        boolean replacePdf = hasFile(file) || mediaId != null;

        Bulletin bulletin = load(id);
        checkVersion(bulletin, version); // 낙관락 확인을 업로드보다 먼저 — 충돌 시 파일 쓰기 없음(설계 §6.1)
        Long resolvedMediaId = replacePdf ? resolveMedia(file, mediaId, false, uploaderId) : null;

        bulletin.applyPatch(title, serviceDate, resolvedMediaId);
        repository.flush(); // 버전 UPDATE 즉시 반영 → 응답 version이 post-increment (notice 패턴)
        return detail(bulletin);
    }

    @Transactional
    public void delete(Long id) {
        load(id).softDelete();
    }

    // --- helpers ---

    /** file XOR mediaId를 검증하고 mediaId를 해소한다. 모든 검증은 uploadPdf 이전(설계 §6.1). */
    private long resolveMedia(MultipartFile file, Long mediaId, boolean required, Long uploaderId) {
        boolean hasFile = hasFile(file);
        boolean hasId = mediaId != null;
        if (hasFile && hasId) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "file과 mediaId는 동시에 보낼 수 없습니다");
        }
        if (!hasFile && !hasId) {
            // required=true(create)에서만 도달. patch는 호출 전 replacePdf로 분기해 이 경로로 안 온다.
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "PDF 파일(file) 또는 mediaId가 필요합니다");
        }
        if (hasFile) {
            return mediaService.uploadPdf(file, uploaderId).id();
        }
        mediaService.requirePdf(mediaId);
        return mediaId;
    }

    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private void validateTitle(String title, boolean required) {
        if (title == null) {
            if (required) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "title은 필수입니다");
            }
            return;
        }
        if (!StringUtils.hasText(title) || title.length() > TITLE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "title은 공백일 수 없고 200자 이하여야 합니다");
        }
    }

    private Bulletin load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void checkVersion(Bulletin bulletin, Long expected) {
        if (!bulletin.getVersion().equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private BulletinDetailResponse detail(Bulletin b) {
        return new BulletinDetailResponse(
                b.getId(),
                b.getTitle(),
                b.getServiceDate(),
                b.getMediaId(),
                authorDisplayService.displayName(b.getUpdatedBy()),
                b.getCreatedAt(),
                b.getUpdatedAt(),
                b.getVersion());
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.bulletin.BulletinServiceTest'`
Expected: PASS (14건).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/bulletin/dto/ src/main/java/com/elipair/church/domain/bulletin/BulletinService.java src/test/java/com/elipair/church/domain/bulletin/BulletinServiceTest.java
git commit -m "feat : 주보 서비스 추가(업로드 전 검증·낙관락·PDF 연결·작성자) #17"
```

---

## Task 5: 컨트롤러(공개·관리) + API 통합 테스트

**Files:**
- Create: `src/main/java/com/elipair/church/domain/bulletin/BulletinController.java`
- Create: `src/main/java/com/elipair/church/domain/bulletin/AdminBulletinController.java`
- Test: `src/test/java/com/elipair/church/domain/bulletin/BulletinApiTest.java`

`@SpringBootTest`는 `application.yml`을 그대로 로드하므로 **Flyway(FK + ON DELETE SET NULL)** 가 켜진 실제 스키마로 돈다. 따라서 차단삭제 + 캐스케이드 null화를 end-to-end로 검증한다(설계 §8).

- [ ] **Step 1: 실패하는 API 테스트 작성**

`src/test/java/com/elipair/church/domain/bulletin/BulletinApiTest.java`:

```java
package com.elipair.church.domain.bulletin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
class BulletinApiTest {

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 10, 11, 12, 13};
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
    private BulletinRepository bulletinRepository;

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
        bulletinRepository.deleteAll();
        mediaRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String adminToken() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(adminId, "uuid-admin", "관리자", 1000),
                        null,
                        List.of("BULLETIN_WRITE", "MEDIA_MANAGE"));
    }

    private String token(String... authorities) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(adminId, "uuid-x", "사용자", 100), null, List.of(authorities));
    }

    private long uploadPdfMedia() throws Exception {
        String json = mockMvc.perform(multipart("/api/admin/media")
                        .file(new MockMultipartFile("file", "c.pdf", "application/pdf", PDF))
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long createBulletinWithFile() throws Exception {
        String json = mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "2026-06-01 주보")
                        .param("serviceDate", "2026-06-01")
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    // ---- 공개 조회 (스펙 §5.13) ----

    @Test
    void list_is_public_and_omits_no_body() throws Exception {
        createBulletinWithFile();
        mockMvc.perform(get("/api/bulletins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("2026-06-01 주보"))
                .andExpect(jsonPath("$.content[0].serviceDate").value("2026-06-01"))
                .andExpect(jsonPath("$.content[0].mediaId").exists())
                .andExpect(jsonPath("$.content[0].author").value("관리목사"));
    }

    @Test
    void get_is_public() throws Exception {
        long id = createBulletinWithFile();
        mockMvc.perform(get("/api/bulletins/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) id))
                .andExpect(jsonPath("$.mediaId").exists())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void get_unknown_is_404() throws Exception {
        mockMvc.perform(get("/api/bulletins/999999")).andExpect(status().isNotFound());
    }

    // ---- 인가 ----

    @Test
    void create_without_bulletin_write_is_403() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .header("Authorization", token("MEDIA_MANAGE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ---- 생성 ----

    @Test
    void create_with_file_returns_201_with_author_and_version() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "부활절 주보")
                        .param("serviceDate", "2026-04-05")
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("부활절 주보"))
                .andExpect(jsonPath("$.serviceDate").value("2026-04-05"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.author").value("관리목사"));
    }

    @Test
    void create_with_existing_mediaId_returns_201() throws Exception {
        long mediaId = uploadPdfMedia();
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .param("title", "기존 PDF 주보")
                        .param("serviceDate", "2026-06-08")
                        .param("mediaId", String.valueOf(mediaId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaId").value((int) mediaId));
    }

    @Test
    void create_with_both_file_and_mediaId_is_400() throws Exception {
        long mediaId = uploadPdfMedia();
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .param("mediaId", String.valueOf(mediaId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_with_neither_file_nor_mediaId_is_400() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_with_blank_title_is_400() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "   ")
                        .param("serviceDate", "2026-06-01")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_missing_serviceDate_is_400() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                        .param("title", "주보")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_with_non_pdf_file_is_400() throws Exception {
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .file(new MockMultipartFile("file", "p.jpg", "application/pdf", JPEG))
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_with_image_mediaId_is_400() throws Exception {
        long imageId = ((Number) JsonPath.read(
                        mockMvc.perform(multipart("/api/admin/media")
                                        .file(new MockMultipartFile("file", "p.jpg", "image/jpeg", JPEG))
                                        .header("Authorization", adminToken()))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(StandardCharsets.UTF_8),
                        "$.id"))
                .longValue();

        mockMvc.perform(multipart("/api/admin/bulletins")
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .param("mediaId", String.valueOf(imageId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    // ---- 수정 (PATCH multipart) ----

    @Test
    void patch_metadata_bumps_version_then_stale_is_409() throws Exception {
        long id = createBulletinWithFile();
        mockMvc.perform(multipart("/api/admin/bulletins/" + id)
                        .param("version", "0")
                        .param("title", "수정된 주보")
                        .header("Authorization", adminToken())
                        .with(req -> {
                            req.setMethod("PATCH");
                            return req;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 주보"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(multipart("/api/admin/bulletins/" + id)
                        .param("version", "0")
                        .param("title", "또수정")
                        .header("Authorization", adminToken())
                        .with(req -> {
                            req.setMethod("PATCH");
                            return req;
                        }))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    // ---- 삭제 + 미디어 차단삭제/캐스케이드 (설계 §2.1, §8) ----

    @Test
    void delete_soft_deletes_then_detail_404() throws Exception {
        long id = createBulletinWithFile();
        mockMvc.perform(delete("/api/admin/bulletins/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/bulletins/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void media_delete_blocked_by_active_bulletin_409() throws Exception {
        long mediaId = uploadPdfMedia();
        mockMvc.perform(multipart("/api/admin/bulletins")
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .param("mediaId", String.valueOf(mediaId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/admin/media/" + mediaId).header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEDIA_IN_USE"))
                .andExpect(jsonPath("$.references[0].type").value("bulletin"));
    }

    @Test
    void media_deletable_after_bulletin_soft_deleted_and_fk_set_null() throws Exception {
        long mediaId = uploadPdfMedia();
        String json = mockMvc.perform(multipart("/api/admin/bulletins")
                        .param("title", "주보")
                        .param("serviceDate", "2026-06-01")
                        .param("mediaId", String.valueOf(mediaId))
                        .header("Authorization", adminToken()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        long bulletinId = ((Number) JsonPath.read(json, "$.id")).longValue();

        // 주보 soft-delete → 더 이상 차단 참조 아님
        mockMvc.perform(delete("/api/admin/bulletins/" + bulletinId).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        // media 하드삭제 성공(FK 위반 없음 — ON DELETE SET NULL)
        mockMvc.perform(delete("/api/admin/media/" + mediaId).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        // soft-deleted 주보의 media_id가 null로 정리됨(fresh read — L1 stale 아님)
        Bulletin dead = bulletinRepository.findById(bulletinId).orElseThrow();
        assertThat(dead.getMediaId()).isNull();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.bulletin.BulletinApiTest'`
Expected: FAIL — `BulletinController`/`AdminBulletinController` 미존재로 컴파일 실패.

- [ ] **Step 3: 공개 컨트롤러 작성**

`src/main/java/com/elipair/church/domain/bulletin/BulletinController.java`:

```java
package com.elipair.church.domain.bulletin;

import com.elipair.church.domain.bulletin.dto.BulletinCardResponse;
import com.elipair.church.domain.bulletin.dto.BulletinDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 주보 공개 조회 API(스펙 §5.13). 비인증 — SecurityConfig anyRequest permitAll. */
@RestController
public class BulletinController {

    private final BulletinService service;

    public BulletinController(BulletinService service) {
        this.service = service;
    }

    @GetMapping("/api/bulletins")
    public Page<BulletinCardResponse> list(
            @PageableDefault(size = 10, sort = "serviceDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/api/bulletins/{id}")
    public BulletinDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
```

- [ ] **Step 4: 관리 컨트롤러 작성**

`src/main/java/com/elipair/church/domain/bulletin/AdminBulletinController.java`:

```java
package com.elipair.church.domain.bulletin;

import com.elipair.church.domain.bulletin.dto.BulletinDetailResponse;
import com.elipair.church.global.security.MemberPrincipal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주보 관리 API(스펙 §5.13). 전 메서드 BULLETIN_WRITE. multipart(file XOR mediaId).
 * 스칼라는 required=false로 받고 필수성·XOR·공백은 서비스에서 검증(업로드 전, 설계 §6.1).
 */
@RestController
@PreAuthorize("hasAuthority('BULLETIN_WRITE')")
public class AdminBulletinController {

    private final BulletinService service;

    public AdminBulletinController(BulletinService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/bulletins")
    public ResponseEntity<BulletinDetailResponse> create(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) Long mediaId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(title, serviceDate, file, mediaId, principal.id()));
    }

    @PatchMapping("/api/admin/bulletins/{id}")
    public BulletinDetailResponse patch(
            @PathVariable Long id,
            @RequestParam(required = false) Long version,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) Long mediaId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return service.patch(id, version, title, serviceDate, file, mediaId, principal.id());
    }

    @DeleteMapping("/api/admin/bulletins/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.bulletin.BulletinApiTest'`
Expected: PASS (16건).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/elipair/church/domain/bulletin/BulletinController.java src/main/java/com/elipair/church/domain/bulletin/AdminBulletinController.java src/test/java/com/elipair/church/domain/bulletin/BulletinApiTest.java
git commit -m "feat : 주보 공개·관리 API 추가 #17"
```

---

## Task 6: 전체 빌드 검증

**Files:** 없음(검증만).

- [ ] **Step 1: 전체 빌드·테스트 실행**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL — 컴파일 + 전체 테스트(기존 전체 + 신규 약 41건) + spotless/jar 통과.

- [ ] **Step 2: 실패 시 대응**

- spotless 포맷 위반: `./gradlew spotlessApply` 후 재빌드, 변경분을 마지막 커밋에 `--amend` 없이 별도 `chore : 포맷 정리 #17`로 추가하거나 해당 feat 커밋에 포함.
- 테스트 실패: 메시지 기준으로 해당 Task로 돌아가 수정 → 재실행. 가짜 통과 금지(실제 PASS 출력 확인 후 완료 처리).

- [ ] **Step 3: (전체 그린이면) 완료**

빌드 그린 + 신규 테스트 전부 PASS를 출력으로 확인했으면 #17 구현 완료.

---

## 검증 매핑 (스펙 → 테스트)

| 스펙/설계 요구 | 검증 |
|---|---|
| 공개 목록 service_date DESC·본문 제외 | `BulletinApiTest.list_is_public_and_omits_no_body` |
| 공개 단건 mediaId 포함 | `get_is_public` |
| 관리 BULLETIN_WRITE 인가 | `create_without_bulletin_write_is_403` |
| 생성 file 경로 / mediaId 경로 | `create_with_file_*` / `create_with_existing_mediaId_*` + 서비스 단위 |
| XOR(둘 다/둘 다 없음) 400 | `create_with_both_*` / `create_with_neither_*` |
| 업로드 전 검증(고아 방지, §6.1) | `BulletinServiceTest.*_before_upload`, `patch_stale_version_*_never_uploads` |
| 비PDF file / 이미지 mediaId 거부 | `create_with_non_pdf_file_is_400` / `create_with_image_mediaId_is_400` |
| PATCH 낙관락 | `patch_metadata_bumps_version_then_stale_is_409` |
| soft delete | `delete_soft_deletes_then_detail_404` |
| media 차단삭제(bulletin 참조) | `media_delete_blocked_by_active_bulletin_409` |
| §2.1 nullable + ON DELETE SET NULL | `MigrationIndexTest.bulletins_media_id_fk_*` + `media_deletable_after_bulletin_soft_deleted_and_fk_set_null` |
| §6 부분 인덱스 | `MigrationIndexTest.bulletins_service_date_*` |
| 작성자 표시(updated_by) | `list`/`create` author 검증 + `AuthorDisplayService`(기존 검증) |
