# 공지(Notice) 도메인 구현 계획 — 이슈 #13 (D8)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 교회 공지를 등록·조회·관리하는 단일 교회용 `notice` 도메인을 추가한다(스펙 §5.7). 상단고정(is_pinned) 우선 정렬, 마크다운 본문 + `media:{id}` 참조, 낙관락, 작성자 표시(updated_by)를 포함한다.

**Architecture:** Sermon(D7)이 세운 패턴을 그대로 답습하는 단순 형제 도메인. `BaseEntity` 상속, 원자적 조회수 증가, 명시적 `@Version` 비교 + `repository.flush()`로 응답 version 정합, `MediaReferenceProvider` 구현(경계 안전 정규식), 기존 `ContentTagService`·`AuthorDisplayService` 재사용. 경로 인가는 기존 `SecurityConfig` 3분법으로 충족. 설계 문서: `docs/superpowers/specs/2026-06-06-notice-domain-design.md`.

**Tech Stack:** Spring Boot 4.0.6 / Java 21 / Spring Data JPA / PostgreSQL + Flyway / Spring Security(JWT) / Testcontainers / JUnit5 + Mockito + AssertJ / Lombok / Spotless(palantirJavaFormat).

---

## 사전 메모 (실행자 필독)

- **커밋/푸시는 프로젝트 관례상 "요청 시에만"** 한다(`CLAUDE.md`). 각 Task의 커밋 스텝은 사용자가 승인하면 실행한다. 무단 커밋 금지.
- **커밋 메시지 금지사항: `Co-Authored-By` 태그 절대 추가 금지.** 형식은 `<type> : <설명> #13`(콜론 앞 공백, 한글).
- **버전/체인지로그 파일 손대지 말 것**(`version.yml`, `build.gradle` version, `CHANGELOG.*`). 자동화 소유.
- 포맷 검증: `./gradlew build`는 `spotlessCheck`를 포함한다. 포맷 위반 시 **`./gradlew spotlessApply`** 후 다시 빌드한다.
- 모든 신규 코드는 `com.elipair.church.domain.notice` 패키지. Sermon 파일과 1:1 대응되며 차이는 필드 셋·`is_pinned`·정렬·flush뿐.
- 확정 사실(검증 완료): `NOTICE_WRITE`는 `V2__create_rbac.sql:35`에 시드됨 · `ContentResourceType.NOTICE` 존재 · `BaseEntity`에 `updatedAt`/`createdBy`/`updatedBy`/`version`(Long)/`softDelete()`/`isDeleted()` 존재 · 다음 마이그레이션 번호는 **V8**(V7=sermons).

## File Structure (생성/수정 파일 맵)

**생성 — main**
- `src/main/resources/db/migration/V8__create_notices.sql` — notices 테이블 + 부분 인덱스.
- `src/main/java/com/elipair/church/domain/notice/Notice.java` — 엔티티(BaseEntity 상속).
- `src/main/java/com/elipair/church/domain/notice/NoticeRefRow.java` — 참조추적 인터페이스 프로젝션.
- `src/main/java/com/elipair/church/domain/notice/NoticeRepository.java` — JpaRepository + Spec + 원자 증가 + 참조 네이티브쿼리.
- `src/main/java/com/elipair/church/domain/notice/NoticeSpecifications.java` — 동적 필터(q·taggedIds).
- `src/main/java/com/elipair/church/domain/notice/NoticeReferenceProvider.java` — MediaReferenceProvider 구현.
- `src/main/java/com/elipair/church/domain/notice/NoticeService.java` — 도메인 서비스.
- `src/main/java/com/elipair/church/domain/notice/NoticeController.java` — 공개 조회 API.
- `src/main/java/com/elipair/church/domain/notice/AdminNoticeController.java` — 관리 API(NOTICE_WRITE).
- `src/main/java/com/elipair/church/domain/notice/dto/NoticeCreateRequest.java`
- `src/main/java/com/elipair/church/domain/notice/dto/NoticeUpdateRequest.java`
- `src/main/java/com/elipair/church/domain/notice/dto/NoticePatchRequest.java`
- `src/main/java/com/elipair/church/domain/notice/dto/NoticeCardResponse.java`
- `src/main/java/com/elipair/church/domain/notice/dto/NoticeDetailResponse.java`

**생성 — test**
- `src/test/java/com/elipair/church/domain/notice/NoticeRepositoryTest.java`
- `src/test/java/com/elipair/church/domain/notice/NoticeReferenceProviderTest.java`
- `src/test/java/com/elipair/church/domain/notice/NoticeServiceTest.java`
- `src/test/java/com/elipair/church/domain/notice/NoticeApiTest.java`

**수정 없음(확인 완료):** `SecurityConfig`(3분법 충족), `GlobalExceptionHandler`(낙관락 매핑 완료), `ContentResourceType`(NOTICE 존재), `ContentTagService`·`AuthorDisplayService`(그대로 재사용).

---

## Task 1: 영속성 기반 (마이그레이션 + 엔티티 + 리포지토리 + Spec)

**Files:**
- Create: `src/main/resources/db/migration/V8__create_notices.sql`
- Create: `src/main/java/com/elipair/church/domain/notice/Notice.java`
- Create: `src/main/java/com/elipair/church/domain/notice/NoticeRefRow.java`
- Create: `src/main/java/com/elipair/church/domain/notice/NoticeRepository.java`
- Create: `src/main/java/com/elipair/church/domain/notice/NoticeSpecifications.java`
- Test: `src/test/java/com/elipair/church/domain/notice/NoticeRepositoryTest.java`

- [ ] **Step 1: 실패하는 리포지토리 슬라이스 테스트 작성**

`src/test/java/com/elipair/church/domain/notice/NoticeRepositoryTest.java`:

```java
package com.elipair.church.domain.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class NoticeRepositoryTest {

    @Autowired
    private NoticeRepository repository;

    private Notice notice(String title) {
        return Notice.create(title, "본문", false);
    }

    @Test
    void save_populates_audit_columns() {
        Notice saved = repository.saveAndFlush(notice("공지 A"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.isPinned()).isFalse();
    }

    @Test
    void findByIdAndDeletedAtIsNull_excludes_soft_deleted() {
        Notice active = repository.saveAndFlush(notice("활성"));
        Notice deleted = notice("삭제");
        deleted.softDelete();
        Notice savedDeleted = repository.saveAndFlush(deleted);

        assertThat(repository.findByIdAndDeletedAtIsNull(active.getId())).isPresent();
        assertThat(repository.findByIdAndDeletedAtIsNull(savedDeleted.getId())).isEmpty();
    }

    @Test
    void incrementViewCount_is_atomic_and_skips_deleted() {
        Notice n = repository.saveAndFlush(notice("조회수"));

        int updated = repository.incrementViewCount(n.getId());

        assertThat(updated).isEqualTo(1);
        assertThat(repository
                        .findByIdAndDeletedAtIsNull(n.getId())
                        .orElseThrow()
                        .getViewCount())
                .isEqualTo(1L);
    }

    @Test
    void incrementViewCount_returns_zero_for_deleted() {
        Notice deleted = notice("삭제됨");
        deleted.softDelete();
        Notice saved = repository.saveAndFlush(deleted);

        assertThat(repository.incrementViewCount(saved.getId())).isZero();
    }

    @Test
    void filter_q_matches_title_case_insensitively() {
        repository.saveAndFlush(notice("부활절 안내"));
        repository.saveAndFlush(notice("성탄절 안내"));

        assertThat(repository
                        .findAll(NoticeSpecifications.filter("부활", null), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
    }

    @Test
    void filter_taggedIds_empty_returns_none_and_excludes_deleted() {
        Notice n = repository.saveAndFlush(notice("A"));
        Notice deleted = notice("D");
        deleted.softDelete();
        repository.saveAndFlush(deleted);

        assertThat(repository
                        .findAll(NoticeSpecifications.filter(null, List.of()), PageRequest.of(0, 10))
                        .getTotalElements())
                .isZero();
        assertThat(repository
                        .findAll(NoticeSpecifications.filter(null, List.of(n.getId())), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
        assertThat(repository
                        .findAll(NoticeSpecifications.filter(null, null), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인(RED)**

Run: `./gradlew test --tests 'com.elipair.church.domain.notice.NoticeRepositoryTest'`
Expected: 컴파일 실패 — `Notice`, `NoticeRepository`, `NoticeSpecifications` 심볼 없음.

- [ ] **Step 3: 마이그레이션 작성**

`src/main/resources/db/migration/V8__create_notices.sql`:

```sql
-- 공지 콘텐츠(스펙 §5.7). BaseEntity 상속, 감사/소프트삭제/낙관락 컬럼은 V7(sermons) 관례를 따른다.
-- 본문 content는 마크다운 원본(TEXT), 본문 내 이미지는 media:{id}로 참조(스펙 §5). V7은 sermons가 점유 → V8.
CREATE TABLE notices (
    id          BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    content     TEXT,
    is_pinned   BOOLEAN      NOT NULL DEFAULT FALSE,
    view_count  BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,
    created_by  BIGINT       REFERENCES members (id),
    updated_by  BIGINT       REFERENCES members (id),
    deleted_at  TIMESTAMP,
    version     BIGINT       NOT NULL DEFAULT 0
);

-- 목록 기본 정렬 = is_pinned DESC, created_at DESC, 미삭제만(스펙 §6 부분 인덱스).
CREATE INDEX idx_notices_pinned_created ON notices (is_pinned DESC, created_at DESC) WHERE deleted_at IS NULL;
```

- [ ] **Step 4: 엔티티 작성**

`src/main/java/com/elipair/church/domain/notice/Notice.java`:

```java
package com.elipair.church.domain.notice;

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
 * 공지(스펙 §5.7). 수정가능 콘텐츠라 BaseEntity(감사·소프트삭제·낙관락)를 상속.
 * viewCount는 앱 코드용 세터가 없다 — 오직 리포지토리 원자 쿼리로만 증가(@Version 미증가).
 * created_by/updated_by는 AuditorAware가 자동 주입(서비스 수동 세팅 안 함).
 */
@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_pinned", nullable = false)
    private boolean isPinned;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    private Notice(String title, String content, boolean isPinned) {
        this.title = title;
        this.content = content;
        this.isPinned = isPinned;
        this.viewCount = 0L;
    }

    public static Notice create(String title, String content, boolean isPinned) {
        return new Notice(title, content, isPinned);
    }

    /** PUT 전체 교체 — viewCount·감사필드 제외 전 필드를 요청값으로 덮어쓴다. */
    public void update(String title, String content, boolean isPinned) {
        this.title = title;
        this.content = content;
        this.isPinned = isPinned;
    }

    /** PATCH 부분 수정 — null 인자는 미변경(상단고정 토글 포함). */
    public void applyPatch(String title, String content, Boolean isPinned) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (isPinned != null) {
            this.isPinned = isPinned;
        }
    }
}
```

> 참고: Lombok `@Getter`는 boolean 필드 `isPinned`에 대해 `isPinned()` 게터를 생성한다(`getIsPinned` 아님).

- [ ] **Step 5: 참조 프로젝션 작성**

`src/main/java/com/elipair/church/domain/notice/NoticeRefRow.java`:

```java
package com.elipair.church.domain.notice;

/** 미디어 참조 추적용 인터페이스 프로젝션 — (id, title) 한 행. */
public interface NoticeRefRow {
    Long getId();

    String getTitle();
}
```

- [ ] **Step 6: 리포지토리 작성**

`src/main/java/com/elipair/church/domain/notice/NoticeRepository.java`:

```java
package com.elipair.church.domain.notice;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long>, JpaSpecificationExecutor<Notice> {

    Optional<Notice> findByIdAndDeletedAtIsNull(Long id);

    /** 상세 조회 조회수 +1. 벌크 UPDATE라 @Version·감사필드를 건드리지 않는다(락 우회). clear로 L1 stale 방지. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notice n set n.viewCount = n.viewCount + 1 where n.id = :id and n.deletedAt is null")
    int incrementViewCount(@Param("id") Long id);

    /**
     * 본문이 media:{id}를 참조하는 미삭제 공지(id·title). PG 정규식 ~ 로 경계 안전 매칭.
     * pattern 예: "media:42($|[^0-9])" — 42가 media:420/421에 매칭되지 않는다.
     */
    @Query(
            value = "select id as id, title as title from notices " + "where deleted_at is null and content ~ :pattern",
            nativeQuery = true)
    List<NoticeRefRow> findReferencesByMedia(@Param("pattern") String pattern);
}
```

- [ ] **Step 7: Specification 작성**

`src/main/java/com/elipair/church/domain/notice/NoticeSpecifications.java`:

```java
package com.elipair.church.domain.notice;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * 공지 동적 필터(스펙 §5.7). null 인자는 술어에서 제외. 항상 미삭제만(deletedAt IS NULL).
 * q는 title만 검색(공지 카드 메타 텍스트가 title뿐). taggedIds는 서비스가 미리 해석해 넘긴 id 목록 — 순수 조건 빌더로 유지.
 */
final class NoticeSpecifications {

    private NoticeSpecifications() {}

    static Specification<Notice> filter(String q, List<Long> taggedIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), like));
            }
            if (taggedIds != null) {
                predicates.add(taggedIds.isEmpty() ? cb.disjunction() : root.get("id").in(taggedIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

- [ ] **Step 8: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.notice.NoticeRepositoryTest'`
Expected: BUILD SUCCESSFUL, 6개 테스트 PASS.

- [ ] **Step 9: 커밋(사용자 승인 시)**

```bash
git add src/main/resources/db/migration/V8__create_notices.sql \
  src/main/java/com/elipair/church/domain/notice/Notice.java \
  src/main/java/com/elipair/church/domain/notice/NoticeRefRow.java \
  src/main/java/com/elipair/church/domain/notice/NoticeRepository.java \
  src/main/java/com/elipair/church/domain/notice/NoticeSpecifications.java \
  src/test/java/com/elipair/church/domain/notice/NoticeRepositoryTest.java
git commit -m "feat : 공지 엔티티·리포지토리·동적 검색·V8 마이그레이션 추가 #13"
```

---

## Task 2: 미디어 참조 추적 (NoticeReferenceProvider)

**Files:**
- Create: `src/main/java/com/elipair/church/domain/notice/NoticeReferenceProvider.java`
- Test: `src/test/java/com/elipair/church/domain/notice/NoticeReferenceProviderTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/elipair/church/domain/notice/NoticeReferenceProviderTest.java`:

```java
package com.elipair.church.domain.notice;

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
class NoticeReferenceProviderTest {

    @Autowired
    private NoticeRepository repository;

    private NoticeReferenceProvider provider;

    @BeforeEach
    void init() {
        provider = new NoticeReferenceProvider(repository);
    }

    private Notice withBody(String title, String body) {
        return Notice.create(title, body, false);
    }

    @Test
    void matches_exact_id_not_prefix_collision() {
        repository.saveAndFlush(withBody("42참조", "본문 ![](media:42) 끝"));
        repository.saveAndFlush(withBody("420참조", "본문 ![](media:420) 끝"));

        List<ContentRef> refs = provider.findReferences(42);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).type()).isEqualTo("notice");
        assertThat(refs.get(0).title()).isEqualTo("42참조");
    }

    @Test
    void matches_when_id_at_end_of_body() {
        repository.saveAndFlush(withBody("끝참조", "마지막 이미지 media:7"));

        assertThat(provider.findReferences(7)).hasSize(1);
    }

    @Test
    void excludes_soft_deleted() {
        Notice deleted = withBody("삭제", "![](media:9)");
        deleted.softDelete();
        repository.saveAndFlush(deleted);

        assertThat(provider.findReferences(9)).isEmpty();
    }

    @Test
    void no_reference_returns_empty() {
        repository.saveAndFlush(withBody("무관", "그림 없음"));

        assertThat(provider.findReferences(1)).isEmpty();
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인(RED)**

Run: `./gradlew test --tests 'com.elipair.church.domain.notice.NoticeReferenceProviderTest'`
Expected: 컴파일 실패 — `NoticeReferenceProvider` 심볼 없음.

- [ ] **Step 3: Provider 구현**

`src/main/java/com/elipair/church/domain/notice/NoticeReferenceProvider.java`:

```java
package com.elipair.church.domain.notice;

import com.elipair.church.domain.media.MediaReferenceProvider;
import com.elipair.church.global.common.ContentRef;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 본문 media:{id} 참조 추적(스펙 §5.10 SPI). MediaService가 빈으로 주입받아 합집합에 더한다.
 * ContentRef.type은 소문자 "notice" — 미디어 참조 API 계약 값(스펙 §5.10). soft-deleted 공지는 제외(자기 치유).
 * 경계 안전: media:42 뒤에 숫자가 오면 매칭하지 않아 42가 420/421에 오탐되지 않는다.
 */
@Component
class NoticeReferenceProvider implements MediaReferenceProvider {

    private final NoticeRepository repository;

    NoticeReferenceProvider(NoticeRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ContentRef> findReferences(long mediaId) {
        String pattern = "media:" + mediaId + "($|[^0-9])";
        return repository.findReferencesByMedia(pattern).stream()
                .map(row -> new ContentRef("notice", row.getId(), row.getTitle()))
                .toList();
    }
}
```

- [ ] **Step 4: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.notice.NoticeReferenceProviderTest'`
Expected: BUILD SUCCESSFUL, 4개 테스트 PASS.

- [ ] **Step 5: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/domain/notice/NoticeReferenceProvider.java \
  src/test/java/com/elipair/church/domain/notice/NoticeReferenceProviderTest.java
git commit -m "feat : 공지 미디어 참조추적 Provider 추가 #13"
```

---

## Task 3: DTO 5종

**Files:**
- Create: `src/main/java/com/elipair/church/domain/notice/dto/NoticeCreateRequest.java`
- Create: `src/main/java/com/elipair/church/domain/notice/dto/NoticeUpdateRequest.java`
- Create: `src/main/java/com/elipair/church/domain/notice/dto/NoticePatchRequest.java`
- Create: `src/main/java/com/elipair/church/domain/notice/dto/NoticeCardResponse.java`
- Create: `src/main/java/com/elipair/church/domain/notice/dto/NoticeDetailResponse.java`

> DTO는 다음 Task의 서비스/컨트롤러 컴파일에 필요하므로 여기서 먼저 만든다. 테스트는 Task 4·5에서 이들을 사용한다.

- [ ] **Step 1: NoticeCreateRequest**

```java
package com.elipair.church.domain.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 공지 등록(POST) 요청. @Size(max)는 V8 컬럼 길이와 일치. content는 TEXT지만 스펙 §5 최소검증으로 상한 부여. isPinned 미지정 시 false. */
public record NoticeCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50000) String content,
        Boolean isPinned,
        List<Long> tagIds) {}
```

- [ ] **Step 2: NoticeUpdateRequest**

```java
package com.elipair.church.domain.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 공지 전체 수정(PUT) 요청. version은 낙관락 비교용 필수. isPinned null은 false로 간주(전체 교체). */
public record NoticeUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50000) String content,
        Boolean isPinned,
        List<Long> tagIds,
        @NotNull Long version) {}
```

- [ ] **Step 3: NoticePatchRequest**

```java
package com.elipair.church.domain.notice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 공지 부분 수정(PATCH) 요청. 전달된(비-null) 필드만 적용(isPinned 토글 포함). tagIds null이면 태그 미변경. version 필수. */
public record NoticePatchRequest(
        @Size(max = 200) String title,
        @Size(max = 50000) String content,
        Boolean isPinned,
        List<Long> tagIds,
        @NotNull Long version) {}
```

- [ ] **Step 4: NoticeCardResponse**

```java
package com.elipair.church.domain.notice.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/** 공지 목록 카드(스펙 §5.7). content 제외 — 카드용 메타만(제목·고정·조회수·작성일·태그·작성자). author = updated_by 표시 이름. */
public record NoticeCardResponse(
        Long id,
        String title,
        boolean isPinned,
        long viewCount,
        LocalDateTime createdAt,
        List<TagResponse> tags,
        String author) {}
```

- [ ] **Step 5: NoticeDetailResponse**

```java
package com.elipair.church.domain.notice.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/** 공지 상세(스펙 §5.7). content·version 포함(version은 편집 재전송용 — update/patch는 서비스 flush로 post-increment 보장). author = updated_by 표시 이름. */
public record NoticeDetailResponse(
        Long id,
        String title,
        String content,
        boolean isPinned,
        long viewCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version,
        List<TagResponse> tags,
        String author) {}
```

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew spotlessApply && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/domain/notice/dto/
git commit -m "feat : 공지 요청·응답 DTO 추가 #13"
```

---

## Task 4: 서비스 (NoticeService)

**Files:**
- Create: `src/main/java/com/elipair/church/domain/notice/NoticeService.java`
- Test: `src/test/java/com/elipair/church/domain/notice/NoticeServiceTest.java`

- [ ] **Step 1: 실패하는 단위 테스트 작성**

`src/test/java/com/elipair/church/domain/notice/NoticeServiceTest.java`:

```java
package com.elipair.church.domain.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.domain.notice.dto.NoticeCreateRequest;
import com.elipair.church.domain.notice.dto.NoticePatchRequest;
import com.elipair.church.domain.notice.dto.NoticeUpdateRequest;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class NoticeServiceTest {

    private NoticeRepository repository;
    private ContentTagService contentTagService;
    private AuthorDisplayService authorDisplayService;
    private NoticeService service;

    @BeforeEach
    void init() {
        repository = mock(NoticeRepository.class);
        contentTagService = mock(ContentTagService.class);
        authorDisplayService = mock(AuthorDisplayService.class);
        service = new NoticeService(repository, contentTagService, authorDisplayService);
        when(contentTagService.getTags(any(), any())).thenReturn(List.of());
        when(authorDisplayService.displayName(any())).thenReturn("관리자");
    }

    private NoticeCreateRequest createReq() {
        return new NoticeCreateRequest("제목", "본문", false, List.of(1L, 2L));
    }

    private Notice mockNoticeWithVersion(long version) {
        Notice n = mock(Notice.class);
        when(n.getId()).thenReturn(10L);
        when(n.getVersion()).thenReturn(version);
        return n;
    }

    @Test
    void create_persists_and_links_tags() {
        Notice saved = mockNoticeWithVersion(0L);
        when(repository.save(any(Notice.class))).thenReturn(saved);

        service.create(createReq());

        verify(repository).save(any(Notice.class));
        verify(contentTagService).replaceLinks(ContentResourceType.NOTICE, 10L, List.of(1L, 2L));
    }

    @Test
    void update_with_matching_version_replaces_tags_and_flushes() {
        Notice n = mockNoticeWithVersion(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));
        NoticeUpdateRequest req = new NoticeUpdateRequest("새제목", "새본문", true, List.of(5L), 3L);

        service.update(10L, req);

        verify(contentTagService).replaceLinks(ContentResourceType.NOTICE, 10L, List.of(5L));
        verify(repository).flush();
    }

    @Test
    void update_with_stale_version_throws_409_and_skips_changes() {
        Notice n = mockNoticeWithVersion(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));
        NoticeUpdateRequest req = new NoticeUpdateRequest("새제목", "새본문", true, List.of(5L), 2L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(contentTagService, never()).replaceLinks(any(), any(), any());
    }

    @Test
    void patch_with_null_tagIds_keeps_tags_and_flushes() {
        Notice n = mockNoticeWithVersion(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));
        NoticePatchRequest req = new NoticePatchRequest("부분제목", null, null, null, 0L);

        service.patch(10L, req);

        verify(contentTagService, never()).replaceLinks(any(), any(), any());
        verify(repository).flush();
    }

    @Test
    void patch_with_stale_version_throws_409_and_skips_changes() {
        Notice n = mockNoticeWithVersion(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));
        NoticePatchRequest req = new NoticePatchRequest("부분제목", null, null, null, 2L);

        assertThatThrownBy(() -> service.patch(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(contentTagService, never()).replaceLinks(any(), any(), any());
    }

    @Test
    void delete_soft_deletes_and_cleans_tags() {
        Notice n = mockNoticeWithVersion(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));

        service.delete(10L);

        verify(n).softDelete();
        verify(contentTagService).cleanUp(ContentResourceType.NOTICE, 10L);
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
        Notice n = mockNoticeWithVersion(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(n));

        service.get(10L);

        InOrder order = inOrder(repository);
        order.verify(repository).incrementViewCount(10L);
        order.verify(repository).findByIdAndDeletedAtIsNull(10L);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인(RED)**

Run: `./gradlew test --tests 'com.elipair.church.domain.notice.NoticeServiceTest'`
Expected: 컴파일 실패 — `NoticeService` 심볼 없음.

- [ ] **Step 3: 서비스 구현**

`src/main/java/com/elipair/church/domain/notice/NoticeService.java`:

```java
package com.elipair.church.domain.notice;

import com.elipair.church.domain.member.AuthorDisplayService;
import com.elipair.church.domain.notice.dto.NoticeCardResponse;
import com.elipair.church.domain.notice.dto.NoticeCreateRequest;
import com.elipair.church.domain.notice.dto.NoticeDetailResponse;
import com.elipair.church.domain.notice.dto.NoticePatchRequest;
import com.elipair.church.domain.notice.dto.NoticeUpdateRequest;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 서비스(스펙 §5.7). 태그(ContentTagService)·작성자(AuthorDisplayService)와 조립.
 * 조회수는 incrementViewCount(원자 UPDATE, @Version 우회). 낙관락은 명시적 version 비교(백스톱 JPA @Version).
 * update/patch는 detail 생성 전 repository.flush()로 버전 증가를 즉시 반영(stale version 응답 방지 — 설계 §5).
 */
@Service
@Transactional(readOnly = true)
public class NoticeService {

    private static final ContentResourceType TYPE = ContentResourceType.NOTICE;

    private final NoticeRepository repository;
    private final ContentTagService contentTagService;
    private final AuthorDisplayService authorDisplayService;

    public NoticeService(
            NoticeRepository repository,
            ContentTagService contentTagService,
            AuthorDisplayService authorDisplayService) {
        this.repository = repository;
        this.contentTagService = contentTagService;
        this.authorDisplayService = authorDisplayService;
    }

    public Page<NoticeCardResponse> list(String q, Long tagId, Pageable pageable) {
        List<Long> taggedIds = tagId == null ? null : contentTagService.resourceIdsWithTag(TYPE, tagId);
        Page<Notice> page = repository.findAll(NoticeSpecifications.filter(q, taggedIds), pageable);

        List<Long> ids = page.map(Notice::getId).getContent();
        Map<Long, List<TagResponse>> tagsMap = contentTagService.getTagsByResources(TYPE, ids);
        Map<Long, String> authorMap =
                authorDisplayService.displayNames(page.map(Notice::getUpdatedBy).getContent());

        return page.map(n -> new NoticeCardResponse(
                n.getId(),
                n.getTitle(),
                n.isPinned(),
                n.getViewCount(),
                n.getCreatedAt(),
                tagsMap.getOrDefault(n.getId(), List.of()),
                authorMap.getOrDefault(n.getUpdatedBy(), AuthorDisplayService.UNKNOWN)));
    }

    @Transactional
    public NoticeDetailResponse get(Long id) {
        repository.incrementViewCount(id); // 먼저 증가(clearAutomatically) → 아래 재조회가 +1 반영본을 읽음
        Notice notice = repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return detail(notice);
    }

    @Transactional
    public NoticeDetailResponse create(NoticeCreateRequest req) {
        Notice notice = repository.save(Notice.create(req.title(), req.content(), Boolean.TRUE.equals(req.isPinned())));
        contentTagService.replaceLinks(TYPE, notice.getId(), req.tagIds());
        return detail(notice);
    }

    @Transactional
    public NoticeDetailResponse update(Long id, NoticeUpdateRequest req) {
        Notice notice = load(id);
        checkVersion(notice, req.version());
        notice.update(req.title(), req.content(), Boolean.TRUE.equals(req.isPinned()));
        contentTagService.replaceLinks(TYPE, id, req.tagIds());
        repository.flush(); // 버전 UPDATE 즉시 반영 → 응답 version이 post-increment (설계 §5)
        return detail(notice);
    }

    @Transactional
    public NoticeDetailResponse patch(Long id, NoticePatchRequest req) {
        Notice notice = load(id);
        checkVersion(notice, req.version());
        notice.applyPatch(req.title(), req.content(), req.isPinned());
        if (req.tagIds() != null) {
            contentTagService.replaceLinks(TYPE, id, req.tagIds());
        }
        repository.flush(); // 태그 미변경 PATCH도 버전 증가를 응답에 반영 (설계 §5)
        return detail(notice);
    }

    @Transactional
    public void delete(Long id) {
        Notice notice = load(id);
        notice.softDelete();
        contentTagService.cleanUp(TYPE, id);
    }

    private Notice load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void checkVersion(Notice notice, Long expected) {
        if (!notice.getVersion().equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private NoticeDetailResponse detail(Notice n) {
        return new NoticeDetailResponse(
                n.getId(),
                n.getTitle(),
                n.getContent(),
                n.isPinned(),
                n.getViewCount(),
                n.getCreatedAt(),
                n.getUpdatedAt(),
                n.getVersion(),
                contentTagService.getTags(TYPE, n.getId()),
                authorDisplayService.displayName(n.getUpdatedBy()));
    }
}
```

- [ ] **Step 4: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.notice.NoticeServiceTest'`
Expected: BUILD SUCCESSFUL, 8개 테스트 PASS.

- [ ] **Step 5: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/domain/notice/NoticeService.java \
  src/test/java/com/elipair/church/domain/notice/NoticeServiceTest.java
git commit -m "feat : 공지 서비스 추가(낙관락·조회수·태그·작성자·flush 정합) #13"
```

---

## Task 5: 컨트롤러 + E2E API 테스트

**Files:**
- Create: `src/main/java/com/elipair/church/domain/notice/NoticeController.java`
- Create: `src/main/java/com/elipair/church/domain/notice/AdminNoticeController.java`
- Test: `src/test/java/com/elipair/church/domain/notice/NoticeApiTest.java`

- [ ] **Step 1: 실패하는 E2E 테스트 작성**

`src/test/java/com/elipair/church/domain/notice/NoticeApiTest.java`:

```java
package com.elipair.church.domain.notice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NoticeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long authorId;

    @BeforeEach
    void seedAuthor() {
        Member author =
                memberRepository.saveAndFlush(Member.create("01000000000", "관리목사", "{enc}", null, null, true, true));
        authorId = author.getId();
    }

    @AfterEach
    void cleanup() {
        noticeRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String token(Long memberId, String permission) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-" + memberId, "관리자", 1000), null, List.of(permission));
    }

    private String adminToken() {
        return token(authorId, "NOTICE_WRITE");
    }

    private static final String CREATE_BODY =
            """
            {"title":"2026 부활절 안내","content":"본문 ![](media:42)","isPinned":false,"tagIds":[]}
            """;

    private long createNotice(String body) throws Exception {
        String json = mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void create_as_notice_write_returns_201_with_author_and_zero_views() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("2026 부활절 안내"))
                .andExpect(jsonPath("$.content").value("본문 ![](media:42)"))
                .andExpect(jsonPath("$.isPinned").value(false))
                .andExpect(jsonPath("$.viewCount").value(0))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.author").value("관리목사"));
    }

    @Test
    void create_anonymous_is_401() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void create_without_permission_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", token(authorId, "MEDIA_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_blank_title_is_400() throws Exception {
        String bad = CREATE_BODY.replace("2026 부활절 안내", "");
        mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void public_list_paginates_and_omits_content() throws Exception {
        createNotice(CREATE_BODY);
        createNotice(CREATE_BODY);

        mockMvc.perform(get("/api/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").exists())
                .andExpect(jsonPath("$.content[0].author").value("관리목사"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
    }

    @Test
    void public_list_orders_pinned_first() throws Exception {
        // 고정 공지를 먼저(=더 오래된 created_at), 일반 공지를 나중(=더 최신)에 만든다.
        createNotice(
                """
                {"title":"고정공지","content":"c","isPinned":true,"tagIds":[]}
                """);
        createNotice(
                """
                {"title":"일반공지","content":"c","isPinned":false,"tagIds":[]}
                """);

        // 기본 정렬 is_pinned DESC, created_at DESC → 더 오래됐어도 고정이 먼저.
        mockMvc.perform(get("/api/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("고정공지"))
                .andExpect(jsonPath("$.content[0].isPinned").value(true))
                .andExpect(jsonPath("$.content[1].title").value("일반공지"));
    }

    @Test
    void public_detail_increments_view_count() throws Exception {
        long id = createNotice(CREATE_BODY);

        mockMvc.perform(get("/api/notices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1));
        mockMvc.perform(get("/api/notices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(2));
    }

    @Test
    void detail_unknown_is_404() throws Exception {
        mockMvc.perform(get("/api/notices/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void put_full_update_changes_fields_and_bumps_version() throws Exception {
        long id = createNotice(CREATE_BODY);
        String body =
                """
                {"title":"수정된 제목","content":"수정 본문","isPinned":true,"tagIds":[],"version":0}
                """;

        mockMvc.perform(put("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.isPinned").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void put_with_stale_version_is_409() throws Exception {
        long id = createNotice(CREATE_BODY);
        String v0 =
                """
                {"title":"A","content":"c","isPinned":false,"tagIds":[],"version":0}
                """;
        mockMvc.perform(put("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void patch_toggles_pin_and_keeps_other_fields() throws Exception {
        long id = createNotice(CREATE_BODY);
        String body =
                """
                {"isPinned":true,"version":0}
                """;

        mockMvc.perform(patch("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPinned").value(true))
                .andExpect(jsonPath("$.title").value("2026 부활절 안내"));
    }

    @Test
    void patch_response_version_allows_immediate_next_edit() throws Exception {
        long id = createNotice(CREATE_BODY);
        // 1차 PATCH(tagIds 미제공): version 0 → 응답 version은 1이어야 함(flush 반영, stale 409 회피 회귀 가드).
        mockMvc.perform(patch("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"title":"1차수정","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // 응답 version(1)으로 즉시 2차 수정 → stale 409가 아니라 200.
        mockMvc.perform(patch("/api/admin/notices/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"title":"2차수정","version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("2차수정"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void delete_soft_deletes_then_detail_404() throws Exception {
        long id = createNotice(CREATE_BODY);

        mockMvc.perform(delete("/api/admin/notices/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/notices/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void author_is_masked_when_member_withdrawn() throws Exception {
        long id = createNotice(CREATE_BODY);
        Member author = memberRepository.findById(authorId).orElseThrow();
        author.softDelete();
        memberRepository.saveAndFlush(author);

        mockMvc.perform(get("/api/notices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("(탈퇴한 사용자)"));
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인(RED)**

Run: `./gradlew test --tests 'com.elipair.church.domain.notice.NoticeApiTest'`
Expected: 컴파일 실패 — `NoticeController`/`AdminNoticeController` 빈 없음(또는 404 라우팅 실패).

- [ ] **Step 3: 공개 컨트롤러 구현**

`src/main/java/com/elipair/church/domain/notice/NoticeController.java`:

```java
package com.elipair.church.domain.notice;

import com.elipair.church.domain.notice.dto.NoticeCardResponse;
import com.elipair.church.domain.notice.dto.NoticeDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공지 공개 조회 API(스펙 §5.7). 비인증 — SecurityConfig anyRequest permitAll. */
@RestController
public class NoticeController {

    private final NoticeService service;

    public NoticeController(NoticeService service) {
        this.service = service;
    }

    @GetMapping("/api/notices")
    public Page<NoticeCardResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long tagId,
            @PageableDefault(size = 10, sort = {"isPinned", "createdAt"}, direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return service.list(q, tagId, pageable);
    }

    @GetMapping("/api/notices/{id}")
    public NoticeDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
```

> 기본 정렬: `is_pinned DESC, created_at DESC`. PG/JPA에서 boolean DESC는 true(고정)가 먼저. 클라이언트가 `?sort=`로 덮어쓸 수 있는 "기본값"이다(스펙 §5.7 "기본 정렬", 설계 §1).

- [ ] **Step 4: 관리 컨트롤러 구현**

`src/main/java/com/elipair/church/domain/notice/AdminNoticeController.java`:

```java
package com.elipair.church.domain.notice;

import com.elipair.church.domain.notice.dto.NoticeCreateRequest;
import com.elipair.church.domain.notice.dto.NoticeDetailResponse;
import com.elipair.church.domain.notice.dto.NoticePatchRequest;
import com.elipair.church.domain.notice.dto.NoticeUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 공지 관리 API(스펙 §5.7). 전 메서드 NOTICE_WRITE. */
@RestController
@PreAuthorize("hasAuthority('NOTICE_WRITE')")
public class AdminNoticeController {

    private final NoticeService service;

    public AdminNoticeController(NoticeService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/notices")
    public ResponseEntity<NoticeDetailResponse> create(@Valid @RequestBody NoticeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/api/admin/notices/{id}")
    public NoticeDetailResponse update(@PathVariable Long id, @Valid @RequestBody NoticeUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/api/admin/notices/{id}")
    public NoticeDetailResponse patch(@PathVariable Long id, @Valid @RequestBody NoticePatchRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/api/admin/notices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
```

- [ ] **Step 5: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.notice.NoticeApiTest'`
Expected: BUILD SUCCESSFUL, 13개 테스트 PASS.

- [ ] **Step 6: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/domain/notice/NoticeController.java \
  src/main/java/com/elipair/church/domain/notice/AdminNoticeController.java \
  src/test/java/com/elipair/church/domain/notice/NoticeApiTest.java
git commit -m "feat : 공지 공개·관리 API 추가 #13"
```

---

## Task 6: 전체 빌드 + 커버리지 검증

- [ ] **Step 1: 전체 빌드(포맷·전 테스트·자르 조립)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. notice 4개 테스트 클래스 전부 통과, 기존 테스트 회귀 없음.

- [ ] **Step 2: 커버리지 확인(80%+ 목표)**

Run: `./gradlew jacocoTestReport`
Expected: `build/reports/jacoco/test/html/index.html`에서 `domain.notice` 패키지 라인 커버리지 ≥ 80%.
(서비스/엔티티/Spec/Provider/컨트롤러가 4개 테스트로 모두 실행됨 — sermon과 동일 수준.)

- [ ] **Step 3: 최종 정리 커밋(필요 시, 사용자 승인 시)**

> 변경이 남아있을 때만. notice 외 파일은 수정하지 않는다.

---

## Self-Review (작성자 점검 완료)

**1. 스펙 커버리지** — 설계 문서 각 절을 태스크에 매핑:
- §1 데이터 모델(V8·엔티티) → Task 1. is_pinned/부분 인덱스 포함.
- §2 리포지토리/§3 Spec → Task 1. incrementViewCount·findReferencesByMedia·filter(q·taggedIds).
- §4 미디어 참조 → Task 2. 경계 안전 정규식·"notice" 타입·soft-delete 제외.
- §5 서비스(list/get/create/update/patch/delete, flush 정합, 조회수 trade-off) → Task 4.
- §6 API(공개·관리, 기본 정렬, NOTICE_WRITE) → Task 5.
- §7 테스트 4종(부분 인덱스 비검증 명시 포함) → Task 1·2·4·5.
- 리뷰 반영 3건: flush(Task 4·5), repo 테스트 over-claim 제거(Task 1은 인덱스 검증을 주장하지 않음), 조회수 trade-off 주석(Task 4 서비스 get 주석).

**2. 플레이스홀더 스캔** — TBD/TODO/"적절히 처리" 없음. 모든 코드 스텝에 완전한 코드 포함.

**3. 타입 일관성** — `Notice.create(String,String,boolean)`, `update(String,String,boolean)`, `applyPatch(String,String,Boolean)`, `NoticeService(NoticeRepository,ContentTagService,AuthorDisplayService)`, `list(String,Long,Pageable)`, `isPinned()`(Lombok boolean 게터), `ContentResourceType.NOTICE`, `ErrorCode.{RESOURCE_NOT_FOUND,OPTIMISTIC_LOCK_CONFLICT}`, `AuthorDisplayService.UNKNOWN` — 태스크 전반 일치. DTO 필드명(`isPinned`,`version`,`tagIds`)이 엔티티·서비스·테스트·JSON 본문에서 동일.
