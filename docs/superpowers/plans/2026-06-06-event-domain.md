# 일정/행사(Event) 도메인 구현 계획 — 이슈 #14 (D9)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 교회 일정·행사를 등록·조회·관리하는 단일 교회용 `event` 도메인을 추가한다(스펙 §5.6). 달력 범위 조회(`?year=&month=` / `?startDate=&endDate=`, 겹침 의미론), 마크다운 본문 + `media:{id}` 참조, 낙관락을 포함한다. 반복 일정은 1차 범위 제외(단건만), 작성자·조회수·`?q=` 미지원.

**Architecture:** Notice(D8)/Sermon(D7)이 세운 콘텐츠 도메인 패턴을 답습하되 **달력 범위 조회**가 고유점. `BaseEntity` 상속, `end_at` 배타(exclusive) 겹침 술어(off-by-one 차단), 명시적 `@Version` 비교 + `repository.flush()`(엔티티 필드 변경 시 응답 version 정합; tag-only는 version 유지), `MediaReferenceProvider` 구현(경계 안전 정규식), 기존 `ContentTagService` 재사용. **작성자 미노출**이라 `AuthorDisplayService` 미사용, **조회수 없음**이라 `incrementViewCount` 없음. 경로 인가는 기존 `SecurityConfig` 3분법으로 충족. 설계 문서: `docs/superpowers/specs/2026-06-06-event-domain-design.md`.

**Tech Stack:** Spring Boot 4.0.6 / Java 21 / Spring Data JPA / PostgreSQL + Flyway / Spring Security(JWT) / Testcontainers / JUnit5 + Mockito + AssertJ / Lombok / Spotless(palantirJavaFormat).

---

## 사전 메모 (실행자 필독)

- **커밋/푸시는 프로젝트 관례상 "요청 시에만"** 한다(`CLAUDE.md`). 각 Task의 커밋 스텝은 사용자가 승인하면 실행한다. 무단 커밋 금지.
- **커밋 메시지 금지사항: `Co-Authored-By` 태그 절대 추가 금지.** 형식은 `<type> : <설명> #14`(콜론 앞 공백, 한글).
- **버전/체인지로그 파일 손대지 말 것**(`version.yml`, `build.gradle` version, `CHANGELOG.*`). 자동화 소유.
- 포맷 검증: `./gradlew build`는 `spotlessCheck`를 포함한다. 포맷 위반 시 **`./gradlew spotlessApply`** 후 다시 빌드한다.
- 모든 신규 코드는 `com.elipair.church.domain.event` 패키지. Notice 파일과 거의 1:1 대응되며 차이는 필드 셋·범위조회(DateRange/겹침)·작성자 미노출·조회수 없음·교차검증이다.
- 확정 사실(검증 완료): `EVENT_WRITE`는 `V2__create_rbac.sql:36`에 시드됨 · `ContentResourceType.EVENT` 존재 · `BaseEntity`에 `createdAt`/`updatedAt`/`createdBy`/`updatedBy`/`version`(Long)/`softDelete()`/`isDeleted()` 존재 · `ErrorCode.{INVALID_INPUT_VALUE,RESOURCE_NOT_FOUND,OPTIMISTIC_LOCK_CONFLICT}` 존재 · 다음 마이그레이션 번호는 **V9**(V8=notices) · `JwtTokenProvider.issueAccess(MemberPrincipal, String position, List<String> permissions)` · `MemberPrincipal(Long id, String uuid, String name, int maxPriority)` · `Member.create(phone, name, password, email, positionId, termsAgreed, privacyAgreed)`.

## File Structure (생성/수정 파일 맵)

**생성 — main**
- `src/main/resources/db/migration/V9__create_events.sql` — events 테이블 + 부분 인덱스.
- `src/main/java/com/elipair/church/domain/event/Event.java` — 엔티티(BaseEntity 상속).
- `src/main/java/com/elipair/church/domain/event/EventRefRow.java` — 참조추적 인터페이스 프로젝션.
- `src/main/java/com/elipair/church/domain/event/DateRange.java` — 범위 파라미터 해석·검증 값 객체.
- `src/main/java/com/elipair/church/domain/event/EventRepository.java` — JpaRepository + Spec + 참조 네이티브쿼리.
- `src/main/java/com/elipair/church/domain/event/EventSpecifications.java` — 동적 필터(겹침 range·taggedIds).
- `src/main/java/com/elipair/church/domain/event/EventReferenceProvider.java` — MediaReferenceProvider 구현.
- `src/main/java/com/elipair/church/domain/event/EventService.java` — 도메인 서비스.
- `src/main/java/com/elipair/church/domain/event/EventController.java` — 공개 조회 API.
- `src/main/java/com/elipair/church/domain/event/AdminEventController.java` — 관리 API(EVENT_WRITE).
- `src/main/java/com/elipair/church/domain/event/dto/EventCreateRequest.java`
- `src/main/java/com/elipair/church/domain/event/dto/EventUpdateRequest.java`
- `src/main/java/com/elipair/church/domain/event/dto/EventPatchRequest.java`
- `src/main/java/com/elipair/church/domain/event/dto/EventCardResponse.java`
- `src/main/java/com/elipair/church/domain/event/dto/EventDetailResponse.java`

**생성 — test**
- `src/test/java/com/elipair/church/domain/event/EventRepositoryTest.java`
- `src/test/java/com/elipair/church/domain/event/DateRangeTest.java`
- `src/test/java/com/elipair/church/domain/event/EventReferenceProviderTest.java`
- `src/test/java/com/elipair/church/domain/event/EventServiceTest.java`
- `src/test/java/com/elipair/church/domain/event/EventApiTest.java`

**수정**
- `src/test/java/com/elipair/church/MigrationIndexTest.java` — `idx_events_start_at` 부분 인덱스 검증 1건 추가.

**수정 없음(확인 완료):** `SecurityConfig`(3분법 충족, `/api/events/**` 공개), `GlobalExceptionHandler`(낙관락·BusinessException 매핑 완료), `ContentResourceType`(EVENT 존재), `ContentTagService`(그대로 재사용), `MediaService`(Provider 자동 수집).

---

## Task 1: 영속성 기반 (V9 + 엔티티 + DateRange + 리포지토리 + Spec + 인덱스 검증)

**Files:**
- Create: `src/main/resources/db/migration/V9__create_events.sql`
- Create: `src/main/java/com/elipair/church/domain/event/Event.java`
- Create: `src/main/java/com/elipair/church/domain/event/EventRefRow.java`
- Create: `src/main/java/com/elipair/church/domain/event/DateRange.java`
- Create: `src/main/java/com/elipair/church/domain/event/EventRepository.java`
- Create: `src/main/java/com/elipair/church/domain/event/EventSpecifications.java`
- Test: `src/test/java/com/elipair/church/domain/event/EventRepositoryTest.java`
- Test: `src/test/java/com/elipair/church/domain/event/DateRangeTest.java`
- Modify: `src/test/java/com/elipair/church/MigrationIndexTest.java`

- [ ] **Step 1: 실패하는 DateRange 단위 테스트 작성**

`src/test/java/com/elipair/church/domain/event/DateRangeTest.java`:

```java
package com.elipair.church.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    @Test
    void year_month_builds_half_open_month_range() {
        DateRange r = DateRange.resolve(2026, 6, null, null);
        assertThat(r.from()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(r.toExclusive()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    @Test
    void start_end_builds_inclusive_end_half_open_range() {
        DateRange r = DateRange.resolve(null, null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(r.from()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(r.toExclusive()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    @Test
    void no_params_returns_null() {
        assertThat(DateRange.resolve(null, null, null, null)).isNull();
    }

    @Test
    void year_month_takes_priority_over_date_range() {
        DateRange r = DateRange.resolve(2026, 6, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 2));
        assertThat(r.from()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
    }

    @Test
    void partial_year_month_is_rejected() {
        assertThat(badRequest(() -> DateRange.resolve(2026, null, null, null))).isTrue();
        assertThat(badRequest(() -> DateRange.resolve(null, 6, null, null))).isTrue();
    }

    @Test
    void partial_date_range_is_rejected() {
        assertThat(badRequest(() -> DateRange.resolve(null, null, LocalDate.of(2026, 6, 1), null)))
                .isTrue();
    }

    @Test
    void out_of_range_year_or_month_is_rejected() {
        assertThat(badRequest(() -> DateRange.resolve(0, 6, null, null))).isTrue();
        assertThat(badRequest(() -> DateRange.resolve(10000, 6, null, null))).isTrue();
        assertThat(badRequest(() -> DateRange.resolve(2026, 13, null, null))).isTrue();
    }

    @Test
    void end_before_start_is_rejected() {
        assertThat(badRequest(() -> DateRange.resolve(null, null, LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1))))
                .isTrue();
    }

    private boolean badRequest(Runnable r) {
        try {
            r.run();
            return false;
        } catch (BusinessException e) {
            return e.getErrorCode() == ErrorCode.INVALID_INPUT_VALUE;
        }
    }
}
```

- [ ] **Step 2: 실패하는 리포지토리 슬라이스 테스트 작성**

`src/test/java/com/elipair/church/domain/event/EventRepositoryTest.java`:

```java
package com.elipair.church.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.time.LocalDateTime;
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
class EventRepositoryTest {

    @Autowired
    private EventRepository repository;

    private Event event(String title, LocalDateTime start, LocalDateTime end) {
        return Event.create(title, "본문", "본당", start, end, false);
    }

    private long countInRange(DateRange range) {
        return repository
                .findAll(EventSpecifications.filter(range, null), PageRequest.of(0, 50))
                .getTotalElements();
    }

    private List<String> titlesInRange(DateRange range) {
        return repository
                .findAll(EventSpecifications.filter(range, null), PageRequest.of(0, 50))
                .map(Event::getTitle)
                .getContent();
    }

    @Test
    void save_populates_audit_columns() {
        Event saved = repository.saveAndFlush(
                event("행사", LocalDateTime.of(2026, 6, 10, 10, 0), LocalDateTime.of(2026, 6, 10, 11, 0)));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.isAllDay()).isFalse();
    }

    @Test
    void findByIdAndDeletedAtIsNull_excludes_soft_deleted() {
        Event active = repository.saveAndFlush(
                event("활성", LocalDateTime.of(2026, 6, 10, 10, 0), LocalDateTime.of(2026, 6, 10, 11, 0)));
        Event deleted = event("삭제", LocalDateTime.of(2026, 6, 11, 10, 0), LocalDateTime.of(2026, 6, 11, 11, 0));
        deleted.softDelete();
        Event savedDeleted = repository.saveAndFlush(deleted);

        assertThat(repository.findByIdAndDeletedAtIsNull(active.getId())).isPresent();
        assertThat(repository.findByIdAndDeletedAtIsNull(savedDeleted.getId())).isEmpty();
    }

    @Test
    void range_includes_event_inside_month_and_excludes_other_months() {
        repository.saveAndFlush(
                event("6월행사", LocalDateTime.of(2026, 6, 10, 10, 0), LocalDateTime.of(2026, 6, 10, 11, 0)));

        assertThat(titlesInRange(DateRange.resolve(2026, 6, null, null))).containsExactly("6월행사");
        assertThat(countInRange(DateRange.resolve(2026, 5, null, null))).isZero();
        assertThat(countInRange(DateRange.resolve(2026, 7, null, null))).isZero();
    }

    @Test
    void multi_day_event_appears_in_every_overlapping_month() {
        repository.saveAndFlush(
                event("수련회", LocalDateTime.of(2026, 6, 28, 0, 0), LocalDateTime.of(2026, 7, 2, 0, 0)));

        assertThat(titlesInRange(DateRange.resolve(2026, 6, null, null))).containsExactly("수련회");
        assertThat(titlesInRange(DateRange.resolve(2026, 7, null, null))).containsExactly("수련회");
    }

    @Test
    void event_ending_exactly_on_month_boundary_is_not_double_counted() {
        // end_at == 7/1 00:00 (다음 달 시작 경계). end_at 배타라 7월에는 노출되지 않아야 한다(off-by-one 차단).
        repository.saveAndFlush(
                event("경계행사", LocalDateTime.of(2026, 6, 30, 22, 0), LocalDateTime.of(2026, 7, 1, 0, 0)));

        assertThat(titlesInRange(DateRange.resolve(2026, 6, null, null))).containsExactly("경계행사");
        assertThat(countInRange(DateRange.resolve(2026, 7, null, null))).isZero();
    }

    @Test
    void null_end_point_event_matches_by_start_at() {
        // 8/1 00:00 종일/점 이벤트(end_at null). 경계 from(8/1)의 점 이벤트는 8월에 포함되어야 한다.
        repository.saveAndFlush(Event.create(
                "1일종일", "본문", null, LocalDateTime.of(2026, 8, 1, 0, 0), null, true));

        assertThat(titlesInRange(DateRange.resolve(2026, 8, null, null))).containsExactly("1일종일");
        assertThat(countInRange(DateRange.resolve(2026, 7, null, null))).isZero();
    }

    @Test
    void filter_taggedIds_empty_returns_none_and_excludes_deleted() {
        Event a = repository.saveAndFlush(
                event("A", LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0)));
        Event deleted = event("D", LocalDateTime.of(2026, 6, 2, 10, 0), LocalDateTime.of(2026, 6, 2, 11, 0));
        deleted.softDelete();
        repository.saveAndFlush(deleted);

        assertThat(repository
                        .findAll(EventSpecifications.filter(null, List.of()), PageRequest.of(0, 10))
                        .getTotalElements())
                .isZero();
        assertThat(repository
                        .findAll(EventSpecifications.filter(null, List.of(a.getId())), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
        assertThat(repository
                        .findAll(EventSpecifications.filter(null, null), PageRequest.of(0, 10))
                        .getTotalElements())
                .isEqualTo(1);
    }
}
```

- [ ] **Step 3: 컴파일 실패 확인(RED)**

Run: `./gradlew test --tests 'com.elipair.church.domain.event.EventRepositoryTest' --tests 'com.elipair.church.domain.event.DateRangeTest'`
Expected: 컴파일 실패 — `Event`, `EventRepository`, `EventSpecifications`, `DateRange` 심볼 없음.

- [ ] **Step 4: 마이그레이션 작성**

`src/main/resources/db/migration/V9__create_events.sql`:

```sql
-- 일정/행사 콘텐츠(스펙 §5.6). BaseEntity 상속, 감사/소프트삭제/낙관락 컬럼은 V7(sermons)/V8(notices) 관례를 따른다.
-- 본문 description은 마크다운 원본(TEXT), 본문 내 이미지는 media:{id}로 참조(스펙 §5). V8=notices 점유 → V9.
CREATE TABLE events (
    id          BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    location    VARCHAR(200),
    start_at    TIMESTAMP    NOT NULL,
    end_at      TIMESTAMP,
    all_day     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,
    created_by  BIGINT       REFERENCES members (id),
    updated_by  BIGINT       REFERENCES members (id),
    deleted_at  TIMESTAMP,
    version     BIGINT       NOT NULL DEFAULT 0
);

-- 기본 정렬·범위 조회 = start_at, 미삭제만(스펙 §6 부분 인덱스).
CREATE INDEX idx_events_start_at ON events (start_at) WHERE deleted_at IS NULL;
```

- [ ] **Step 5: 엔티티 작성**

`src/main/java/com/elipair/church/domain/event/Event.java`:

```java
package com.elipair.church.domain.event;

import com.elipair.church.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일정/행사(스펙 §5.6). 수정가능 콘텐츠라 BaseEntity(감사·소프트삭제·낙관락)를 상속.
 * 조회수 없음(스펙 §5.6·§9). created_by/updated_by는 AuditorAware가 자동 주입하되 응답엔 미노출(설계 §1).
 * end_at은 배타(exclusive) 종료, null이면 점 이벤트.
 */
@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 200)
    private String location;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    private Event(
            String title,
            String description,
            String location,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
    }

    public static Event create(
            String title,
            String description,
            String location,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay) {
        return new Event(title, description, location, startAt, endAt, allDay);
    }

    /** PUT 전체 교체 — 감사필드 제외 전 필드를 요청값으로 덮어쓴다. */
    public void update(
            String title,
            String description,
            String location,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startAt = startAt;
        this.endAt = endAt;
        this.allDay = allDay;
    }

    /** PATCH 부분 수정 — null 인자는 미변경(end_at 비우기는 PUT 사용). */
    public void applyPatch(
            String title,
            String description,
            String location,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Boolean allDay) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (location != null) {
            this.location = location;
        }
        if (startAt != null) {
            this.startAt = startAt;
        }
        if (endAt != null) {
            this.endAt = endAt;
        }
        if (allDay != null) {
            this.allDay = allDay;
        }
    }
}
```

> 참고: Lombok `@Getter`는 boolean 필드 `allDay`에 대해 `isAllDay()` 게터를 생성한다(`getAllDay` 아님).

- [ ] **Step 6: 참조 프로젝션 작성**

`src/main/java/com/elipair/church/domain/event/EventRefRow.java`:

```java
package com.elipair.church.domain.event;

/** 미디어 참조 추적용 인터페이스 프로젝션 — (id, title) 한 행. */
public interface EventRefRow {
    Long getId();

    String getTitle();
}
```

- [ ] **Step 7: DateRange 값 객체 작성**

`src/main/java/com/elipair/church/domain/event/DateRange.java`:

```java
package com.elipair.church.domain.event;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 공개 목록의 날짜 범위(반열림 [from, toExclusive)). 파라미터 → 구간 해석·검증을 한곳에 모은다(설계 §3.1·§6.1).
 * year+month 또는 startDate+endDate 한 쌍을 받아 구간을 만든다. 둘 다 없으면 null(범위 없음, 전체).
 * 잘못된 입력(쌍 누락·year/month 범위 밖·endDate<startDate)은 INVALID_INPUT_VALUE.
 * 동시 제공 시 year/month 우선(설계 §6.1).
 */
public record DateRange(LocalDateTime from, LocalDateTime toExclusive) {

    public static DateRange resolve(Integer year, Integer month, LocalDate startDate, LocalDate endDate) {
        boolean hasYearMonth = year != null || month != null;
        boolean hasDateRange = startDate != null || endDate != null;

        if (hasYearMonth && (year == null || month == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (hasDateRange && (startDate == null || endDate == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (year != null && month != null) { // year/month 우선
            if (year < 1 || year > 9999 || month < 1 || month > 12) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            LocalDate first = LocalDate.of(year, month, 1);
            return new DateRange(first.atStartOfDay(), first.plusMonths(1).atStartOfDay());
        }
        if (startDate != null && endDate != null) {
            if (endDate.isBefore(startDate)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return new DateRange(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        }
        return null; // 범위 없음 → 전체
    }
}
```

- [ ] **Step 8: 리포지토리 작성**

`src/main/java/com/elipair/church/domain/event/EventRepository.java`:

```java
package com.elipair.church.domain.event;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    Optional<Event> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 본문(description)이 media:{id}를 참조하는 미삭제 일정(id·title). PG 정규식 ~ 로 경계 안전 매칭.
     * pattern 예: "media:42($|[^0-9])" — 42가 media:420/421에 매칭되지 않는다.
     */
    @Query(
            value =
                    "select id as id, title as title from events where deleted_at is null and description ~ :pattern",
            nativeQuery = true)
    List<EventRefRow> findReferencesByMedia(@Param("pattern") String pattern);
}
```

- [ ] **Step 9: Specification 작성**

`src/main/java/com/elipair/church/domain/event/EventSpecifications.java`:

```java
package com.elipair.church.domain.event;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 일정 동적 필터(스펙 §5.6). null 인자는 술어에서 제외. 항상 미삭제만(deletedAt IS NULL).
 * range 겹침은 end_at 배타: start_at < toExclusive AND (end_at > from OR (end_at IS NULL AND start_at >= from)).
 *   - end_at 배타라 경계 from과 같은 end_at은 제외(off-by-one 차단), null 점 이벤트는 start_at으로 포함.
 * taggedIds는 서비스가 미리 해석해 넘긴 id 목록 — 순수 조건 빌더로 유지.
 */
final class EventSpecifications {

    private EventSpecifications() {}

    static Specification<Event> filter(DateRange range, List<Long> taggedIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (range != null) {
                LocalDateTime from = range.from();
                LocalDateTime toExclusive = range.toExclusive();
                predicates.add(cb.lessThan(root.<LocalDateTime>get("startAt"), toExclusive));
                predicates.add(cb.or(
                        cb.greaterThan(root.<LocalDateTime>get("endAt"), from),
                        cb.and(
                                cb.isNull(root.get("endAt")),
                                cb.greaterThanOrEqualTo(root.<LocalDateTime>get("startAt"), from))));
            }
            if (taggedIds != null) {
                predicates.add(taggedIds.isEmpty() ? cb.disjunction() : root.get("id").in(taggedIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

- [ ] **Step 10: MigrationIndexTest에 events 인덱스 검증 추가**

`src/test/java/com/elipair/church/MigrationIndexTest.java` — 기존 `members_phone_unique_is_partial_on_active_rows` 테스트 메서드 **다음에** 아래 메서드를 추가한다(클래스 닫는 `}` 직전):

```java
    @Test
    void events_start_at_is_partial_on_active_rows() {
        assertThat(indexDef("idx_events_start_at"))
                .as("V9 일정 시작일 범위 인덱스")
                .isNotNull()
                .contains("start_at")
                .contains("deleted_at IS NULL");
    }
```

- [ ] **Step 11: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.event.EventRepositoryTest' --tests 'com.elipair.church.domain.event.DateRangeTest' --tests 'com.elipair.church.MigrationIndexTest'`
Expected: BUILD SUCCESSFUL. EventRepositoryTest 7 PASS, DateRangeTest 8 PASS, MigrationIndexTest 4 PASS(기존 3 + events 1).

- [ ] **Step 12: 커밋(사용자 승인 시)**

```bash
git add src/main/resources/db/migration/V9__create_events.sql \
  src/main/java/com/elipair/church/domain/event/Event.java \
  src/main/java/com/elipair/church/domain/event/EventRefRow.java \
  src/main/java/com/elipair/church/domain/event/DateRange.java \
  src/main/java/com/elipair/church/domain/event/EventRepository.java \
  src/main/java/com/elipair/church/domain/event/EventSpecifications.java \
  src/test/java/com/elipair/church/domain/event/EventRepositoryTest.java \
  src/test/java/com/elipair/church/domain/event/DateRangeTest.java \
  src/test/java/com/elipair/church/MigrationIndexTest.java
git commit -m "feat : 일정 엔티티·리포지토리·범위Spec·DateRange·V9 마이그레이션 추가 #14"
```

---

## Task 2: 미디어 참조 추적 (EventReferenceProvider)

**Files:**
- Create: `src/main/java/com/elipair/church/domain/event/EventReferenceProvider.java`
- Test: `src/test/java/com/elipair/church/domain/event/EventReferenceProviderTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/elipair/church/domain/event/EventReferenceProviderTest.java`:

```java
package com.elipair.church.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.common.ContentRef;
import com.elipair.church.global.config.JpaConfig;
import java.time.LocalDateTime;
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
class EventReferenceProviderTest {

    @Autowired
    private EventRepository repository;

    private EventReferenceProvider provider;

    @BeforeEach
    void init() {
        provider = new EventReferenceProvider(repository);
    }

    private Event withBody(String title, String body) {
        return Event.create(title, body, "본당", LocalDateTime.of(2026, 6, 1, 10, 0), LocalDateTime.of(2026, 6, 1, 11, 0), false);
    }

    @Test
    void matches_exact_id_not_prefix_collision() {
        repository.saveAndFlush(withBody("42참조", "본문 ![](media:42) 끝"));
        repository.saveAndFlush(withBody("420참조", "본문 ![](media:420) 끝"));

        List<ContentRef> refs = provider.findReferences(42);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).type()).isEqualTo("event");
        assertThat(refs.get(0).title()).isEqualTo("42참조");
    }

    @Test
    void matches_when_id_at_end_of_body() {
        repository.saveAndFlush(withBody("끝참조", "마지막 이미지 media:7"));

        assertThat(provider.findReferences(7)).hasSize(1);
    }

    @Test
    void excludes_soft_deleted() {
        Event deleted = withBody("삭제", "![](media:9)");
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

Run: `./gradlew test --tests 'com.elipair.church.domain.event.EventReferenceProviderTest'`
Expected: 컴파일 실패 — `EventReferenceProvider` 심볼 없음.

- [ ] **Step 3: Provider 구현**

`src/main/java/com/elipair/church/domain/event/EventReferenceProvider.java`:

```java
package com.elipair.church.domain.event;

import com.elipair.church.domain.media.MediaReferenceProvider;
import com.elipair.church.global.common.ContentRef;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 본문(description) media:{id} 참조 추적(스펙 §5.10 SPI). MediaService가 빈으로 주입받아 합집합에 더한다.
 * ContentRef.type은 소문자 "event" — 미디어 참조 API 계약 값(스펙 §5.10 UNION). soft-deleted 일정은 제외(자기 치유).
 * 경계 안전: media:42 뒤에 숫자가 오면 매칭하지 않아 42가 420/421에 오탐되지 않는다.
 */
@Component
class EventReferenceProvider implements MediaReferenceProvider {

    private final EventRepository repository;

    EventReferenceProvider(EventRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ContentRef> findReferences(long mediaId) {
        String pattern = "media:" + mediaId + "($|[^0-9])";
        return repository.findReferencesByMedia(pattern).stream()
                .map(row -> new ContentRef("event", row.getId(), row.getTitle()))
                .toList();
    }
}
```

- [ ] **Step 4: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.event.EventReferenceProviderTest'`
Expected: BUILD SUCCESSFUL, 4개 테스트 PASS.

- [ ] **Step 5: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/domain/event/EventReferenceProvider.java \
  src/test/java/com/elipair/church/domain/event/EventReferenceProviderTest.java
git commit -m "feat : 일정 미디어 참조추적 Provider 추가 #14"
```

---

## Task 3: DTO 5종

**Files:**
- Create: `src/main/java/com/elipair/church/domain/event/dto/EventCreateRequest.java`
- Create: `src/main/java/com/elipair/church/domain/event/dto/EventUpdateRequest.java`
- Create: `src/main/java/com/elipair/church/domain/event/dto/EventPatchRequest.java`
- Create: `src/main/java/com/elipair/church/domain/event/dto/EventCardResponse.java`
- Create: `src/main/java/com/elipair/church/domain/event/dto/EventDetailResponse.java`

> DTO는 다음 Task의 서비스/컨트롤러 컴파일에 필요하므로 여기서 먼저 만든다. 테스트는 Task 4·5에서 이들을 사용한다.

- [ ] **Step 1: EventCreateRequest**

`src/main/java/com/elipair/church/domain/event/dto/EventCreateRequest.java`:

```java
package com.elipair.church.domain.event.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 일정 등록(POST) 요청. @Size(max)는 V9 컬럼 길이와 일치. description은 TEXT지만 스펙 §5 최소검증 상한. allDay 미지정 시 false. */
public record EventCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50000) String description,
        @Size(max = 200) String location,
        @NotNull LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean allDay,
        List<Long> tagIds) {

    /** end_at 배타 — start_at보다 엄격히 이후(또는 null=점 이벤트). end==start·end<start 거부(설계 §5.1). */
    @AssertTrue(message = "종료 일시는 시작 일시 이후여야 합니다")
    public boolean isEndAfterStart() {
        return endAt == null || startAt == null || endAt.isAfter(startAt);
    }
}
```

- [ ] **Step 2: EventUpdateRequest**

`src/main/java/com/elipair/church/domain/event/dto/EventUpdateRequest.java`:

```java
package com.elipair.church.domain.event.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 일정 전체 수정(PUT) 요청. version은 낙관락 비교용 필수. allDay null은 false로 간주(전체 교체). */
public record EventUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50000) String description,
        @Size(max = 200) String location,
        @NotNull LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean allDay,
        List<Long> tagIds,
        @NotNull Long version) {

    @AssertTrue(message = "종료 일시는 시작 일시 이후여야 합니다")
    public boolean isEndAfterStart() {
        return endAt == null || startAt == null || endAt.isAfter(startAt);
    }
}
```

- [ ] **Step 3: EventPatchRequest**

`src/main/java/com/elipair/church/domain/event/dto/EventPatchRequest.java`:

```java
package com.elipair.church.domain.event.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 일정 부분 수정(PATCH) 요청. 전달된(비-null) 필드만 적용. tagIds null이면 태그 미변경. version 필수.
 * start/end 교차검증은 DB값과 합쳐야 가능하므로 서비스가 수행(설계 §5.1).
 */
public record EventPatchRequest(
        @Size(max = 200) String title,
        @Size(max = 50000) String description,
        @Size(max = 200) String location,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean allDay,
        List<Long> tagIds,
        @NotNull Long version) {}
```

- [ ] **Step 4: EventCardResponse**

`src/main/java/com/elipair/church/domain/event/dto/EventCardResponse.java`:

```java
package com.elipair.church.domain.event.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/** 일정 목록 카드(스펙 §5.6). description·author·viewCount 제외 — 카드용 메타만(제목·장소·기간·종일·태그). */
public record EventCardResponse(
        Long id,
        String title,
        String location,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean allDay,
        List<TagResponse> tags) {}
```

- [ ] **Step 5: EventDetailResponse**

`src/main/java/com/elipair/church/domain/event/dto/EventDetailResponse.java`:

```java
package com.elipair.church.domain.event.dto;

import com.elipair.church.domain.tag.dto.TagResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 일정 상세(스펙 §5.6). description·version 포함(version은 편집 재전송용 — 엔티티 필드 변경 시 flush로 post-increment,
 * tag-only 수정은 version 유지). author·viewCount 없음(설계 §1).
 */
public record EventDetailResponse(
        Long id,
        String title,
        String description,
        String location,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean allDay,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version,
        List<TagResponse> tags) {}
```

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew spotlessApply && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/domain/event/dto/
git commit -m "feat : 일정 요청·응답 DTO 추가 #14"
```

---

## Task 4: 서비스 (EventService)

**Files:**
- Create: `src/main/java/com/elipair/church/domain/event/EventService.java`
- Test: `src/test/java/com/elipair/church/domain/event/EventServiceTest.java`

- [ ] **Step 1: 실패하는 단위 테스트 작성**

`src/test/java/com/elipair/church/domain/event/EventServiceTest.java`:

```java
package com.elipair.church.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.event.dto.EventCreateRequest;
import com.elipair.church.domain.event.dto.EventPatchRequest;
import com.elipair.church.domain.event.dto.EventUpdateRequest;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 10, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 6, 10, 11, 0);

    private EventRepository repository;
    private ContentTagService contentTagService;
    private EventService service;

    @BeforeEach
    void init() {
        repository = mock(EventRepository.class);
        contentTagService = mock(ContentTagService.class);
        service = new EventService(repository, contentTagService);
        when(contentTagService.getTags(any(), any())).thenReturn(List.of());
    }

    private Event mockEvent(long version) {
        Event e = mock(Event.class);
        when(e.getId()).thenReturn(10L);
        when(e.getVersion()).thenReturn(version);
        return e;
    }

    private EventCreateRequest createReq() {
        return new EventCreateRequest("행사", "본문", "본당", START, END, false, List.of(1L, 2L));
    }

    @Test
    void create_persists_and_links_tags() {
        Event saved = mockEvent(0L);
        when(repository.save(any(Event.class))).thenReturn(saved);

        service.create(createReq());

        verify(repository).save(any(Event.class));
        verify(contentTagService).replaceLinks(ContentResourceType.EVENT, 10L, List.of(1L, 2L));
    }

    @Test
    void update_with_matching_version_replaces_tags_and_flushes() {
        Event e = mockEvent(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));
        EventUpdateRequest req = new EventUpdateRequest("새행사", "새본문", "교육관", START, END, true, List.of(5L), 3L);

        service.update(10L, req);

        verify(e).update("새행사", "새본문", "교육관", START, END, true);
        verify(contentTagService).replaceLinks(ContentResourceType.EVENT, 10L, List.of(5L));
        verify(repository).flush();
    }

    @Test
    void update_with_stale_version_throws_409_and_skips_changes() {
        Event e = mockEvent(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));
        EventUpdateRequest req = new EventUpdateRequest("새행사", "새본문", "교육관", START, END, true, List.of(5L), 2L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(contentTagService, never()).replaceLinks(any(), any(), any());
    }

    @Test
    void patch_with_null_tagIds_keeps_tags_and_flushes() {
        Event e = mockEvent(0L);
        when(e.getStartAt()).thenReturn(START);
        when(e.getEndAt()).thenReturn(END);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));
        EventPatchRequest req = new EventPatchRequest("부분제목", null, null, null, null, null, null, 0L);

        service.patch(10L, req);

        verify(contentTagService, never()).replaceLinks(any(), any(), any());
        verify(repository).flush();
    }

    @Test
    void patch_with_stale_version_throws_409() {
        Event e = mockEvent(3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));
        EventPatchRequest req = new EventPatchRequest("부분제목", null, null, null, null, null, null, 2L);

        assertThatThrownBy(() -> service.patch(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    void patch_with_end_before_start_throws_400_and_skips_mutation() {
        Event e = mockEvent(0L);
        when(e.getStartAt()).thenReturn(START); // 기존 시작 6/10 10:00
        when(e.getEndAt()).thenReturn(END);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));
        // 새 end를 기존 start보다 이전으로(6/10 09:00) → 교차검증 실패.
        EventPatchRequest req = new EventPatchRequest(
                null, null, null, null, LocalDateTime.of(2026, 6, 10, 9, 0), null, null, 0L);

        assertThatThrownBy(() -> service.patch(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(e, never()).applyPatch(any(), any(), any(), any(), any(), any());
        verify(repository, never()).flush();
    }

    @Test
    void delete_soft_deletes_and_cleans_tags() {
        Event e = mockEvent(0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));

        service.delete(10L);

        verify(e).softDelete();
        verify(contentTagService).cleanUp(ContentResourceType.EVENT, 10L);
    }

    @Test
    void get_unknown_throws_404() {
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void get_returns_detail_for_existing() {
        Event e = mockEvent(0L);
        when(e.getStartAt()).thenReturn(START);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(e));

        assertThat(service.get(10L).id()).isEqualTo(10L);
        verify(repository).findByIdAndDeletedAtIsNull(10L);
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인(RED)**

Run: `./gradlew test --tests 'com.elipair.church.domain.event.EventServiceTest'`
Expected: 컴파일 실패 — `EventService` 심볼 없음.

- [ ] **Step 3: 서비스 구현**

`src/main/java/com/elipair/church/domain/event/EventService.java`:

```java
package com.elipair.church.domain.event;

import com.elipair.church.domain.event.dto.EventCardResponse;
import com.elipair.church.domain.event.dto.EventCreateRequest;
import com.elipair.church.domain.event.dto.EventDetailResponse;
import com.elipair.church.domain.event.dto.EventPatchRequest;
import com.elipair.church.domain.event.dto.EventUpdateRequest;
import com.elipair.church.domain.tag.ContentResourceType;
import com.elipair.church.domain.tag.ContentTagService;
import com.elipair.church.domain.tag.dto.TagResponse;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정 서비스(스펙 §5.6). 태그(ContentTagService)와 조립. 작성자 미노출·조회수 없음(설계 §1).
 * 낙관락은 명시적 version 비교(백스톱 JPA @Version). 엔티티 필드 변경 update/patch는 flush로 응답 version 정합;
 * tag-only 수정은 events 행 미변경이라 version 유지(설계 §5 Finding 2). PATCH의 start/end 교차검증은 서비스 책임(설계 §5.1).
 */
@Service
@Transactional(readOnly = true)
public class EventService {

    private static final ContentResourceType TYPE = ContentResourceType.EVENT;

    private final EventRepository repository;
    private final ContentTagService contentTagService;

    public EventService(EventRepository repository, ContentTagService contentTagService) {
        this.repository = repository;
        this.contentTagService = contentTagService;
    }

    public Page<EventCardResponse> list(DateRange range, Long tagId, Pageable pageable) {
        List<Long> taggedIds = tagId == null ? null : contentTagService.resourceIdsWithTag(TYPE, tagId);
        Page<Event> page = repository.findAll(EventSpecifications.filter(range, taggedIds), pageable);

        List<Long> ids = page.map(Event::getId).getContent();
        Map<Long, List<TagResponse>> tagsMap = contentTagService.getTagsByResources(TYPE, ids);

        return page.map(e -> new EventCardResponse(
                e.getId(),
                e.getTitle(),
                e.getLocation(),
                e.getStartAt(),
                e.getEndAt(),
                e.isAllDay(),
                tagsMap.getOrDefault(e.getId(), List.of())));
    }

    public EventDetailResponse get(Long id) {
        return detail(load(id));
    }

    @Transactional
    public EventDetailResponse create(EventCreateRequest req) {
        Event event = repository.save(Event.create(
                req.title(),
                req.description(),
                req.location(),
                req.startAt(),
                req.endAt(),
                Boolean.TRUE.equals(req.allDay())));
        contentTagService.replaceLinks(TYPE, event.getId(), req.tagIds());
        return detail(event);
    }

    @Transactional
    public EventDetailResponse update(Long id, EventUpdateRequest req) {
        Event event = load(id);
        checkVersion(event, req.version());
        event.update(
                req.title(),
                req.description(),
                req.location(),
                req.startAt(),
                req.endAt(),
                Boolean.TRUE.equals(req.allDay()));
        contentTagService.replaceLinks(TYPE, id, req.tagIds());
        repository.flush(); // 엔티티 필드 변경분의 버전 UPDATE 즉시 반영 (설계 §5)
        return detail(event);
    }

    @Transactional
    public EventDetailResponse patch(Long id, EventPatchRequest req) {
        Event event = load(id);
        checkVersion(event, req.version());
        LocalDateTime effectiveStart = req.startAt() != null ? req.startAt() : event.getStartAt();
        LocalDateTime effectiveEnd = req.endAt() != null ? req.endAt() : event.getEndAt();
        if (effectiveEnd != null && !effectiveEnd.isAfter(effectiveStart)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE); // 교차검증(설계 §5.1)
        }
        event.applyPatch(req.title(), req.description(), req.location(), req.startAt(), req.endAt(), req.allDay());
        if (req.tagIds() != null) {
            contentTagService.replaceLinks(TYPE, id, req.tagIds());
        }
        repository.flush();
        return detail(event);
    }

    @Transactional
    public void delete(Long id) {
        Event event = load(id);
        event.softDelete();
        contentTagService.cleanUp(TYPE, id);
    }

    private Event load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void checkVersion(Event event, Long expected) {
        if (!event.getVersion().equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    private EventDetailResponse detail(Event e) {
        return new EventDetailResponse(
                e.getId(),
                e.getTitle(),
                e.getDescription(),
                e.getLocation(),
                e.getStartAt(),
                e.getEndAt(),
                e.isAllDay(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion(),
                contentTagService.getTags(TYPE, e.getId()));
    }
}
```

- [ ] **Step 4: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.event.EventServiceTest'`
Expected: BUILD SUCCESSFUL, 9개 테스트 PASS.

- [ ] **Step 5: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/domain/event/EventService.java \
  src/test/java/com/elipair/church/domain/event/EventServiceTest.java
git commit -m "feat : 일정 서비스 추가(낙관락·태그·교차검증·범위조회) #14"
```

---

## Task 5: 컨트롤러 + E2E API 테스트

**Files:**
- Create: `src/main/java/com/elipair/church/domain/event/EventController.java`
- Create: `src/main/java/com/elipair/church/domain/event/AdminEventController.java`
- Test: `src/test/java/com/elipair/church/domain/event/EventApiTest.java`

- [ ] **Step 1: 실패하는 E2E 테스트 작성**

`src/test/java/com/elipair/church/domain/event/EventApiTest.java`:

```java
package com.elipair.church.domain.event;

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
class EventApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private EventRepository eventRepository;

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
        eventRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String token(Long memberId, String permission) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-" + memberId, "관리자", 1000), null, List.of(permission));
    }

    private String adminToken() {
        return token(authorId, "EVENT_WRITE");
    }

    private long createEvent(String body) throws Exception {
        String json = mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private String body(String title, String start, String end, boolean allDay) {
        String endJson = end == null ? "null" : "\"" + end + "\"";
        return """
                {"title":"%s","description":"본문 ![](media:42)","location":"본당","startAt":"%s","endAt":%s,"allDay":%s,"tagIds":[]}
                """
                .formatted(title, start, endJson, allDay);
    }

    @Test
    void create_as_event_write_returns_201_without_author_or_viewcount() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("부활절 연합예배", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("부활절 연합예배"))
                .andExpect(jsonPath("$.location").value("본당"))
                .andExpect(jsonPath("$.allDay").value(false))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.author").doesNotExist())
                .andExpect(jsonPath("$.viewCount").doesNotExist());
    }

    @Test
    void create_anonymous_is_401() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void create_without_permission_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", token(authorId, "MEDIA_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_blank_title_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_end_not_after_start_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/events")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("뒤집힘", "2026-06-10T11:00:00", "2026-06-10T10:00:00", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void public_list_by_year_month_returns_overlapping_and_omits_description() throws Exception {
        createEvent(body("6월행사", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        createEvent(body("수련회", "2026-06-28T00:00:00", "2026-07-02T00:00:00", false));
        createEvent(body("경계행사", "2026-06-30T22:00:00", "2026-07-01T00:00:00", false));

        // 6월: 세 건 모두 겹친다. 카드에 description 없음.
        mockMvc.perform(get("/api/events").param("year", "2026").param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].description").doesNotExist());
    }

    @Test
    void public_list_july_excludes_boundary_event_keeps_multiday() throws Exception {
        createEvent(body("6월행사", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        createEvent(body("수련회", "2026-06-28T00:00:00", "2026-07-02T00:00:00", false));
        createEvent(body("경계행사", "2026-06-30T22:00:00", "2026-07-01T00:00:00", false));

        // 7월: 수련회만(end_at 배타라 경계행사 제외, off-by-one 차단). start_at ASC.
        mockMvc.perform(get("/api/events").param("year", "2026").param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("수련회"));
    }

    @Test
    void public_list_orders_by_start_at_ascending() throws Exception {
        createEvent(body("나중", "2026-06-20T10:00:00", "2026-06-20T11:00:00", false));
        createEvent(body("먼저", "2026-06-05T10:00:00", "2026-06-05T11:00:00", false));

        mockMvc.perform(get("/api/events").param("year", "2026").param("month", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("먼저"))
                .andExpect(jsonPath("$.content[1].title").value("나중"));
    }

    @Test
    void public_list_by_start_end_date_range() throws Exception {
        createEvent(body("범위안", "2026-06-15T10:00:00", "2026-06-15T11:00:00", false));
        createEvent(body("범위밖", "2026-07-15T10:00:00", "2026-07-15T11:00:00", false));

        mockMvc.perform(get("/api/events").param("startDate", "2026-06-01").param("endDate", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("범위안"));
    }

    @Test
    void public_list_partial_pair_is_400() throws Exception {
        mockMvc.perform(get("/api/events").param("year", "2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void public_list_month_out_of_range_is_400() throws Exception {
        mockMvc.perform(get("/api/events").param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void public_list_end_before_start_is_400() throws Exception {
        mockMvc.perform(get("/api/events").param("startDate", "2026-06-30").param("endDate", "2026-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void detail_returns_description_and_no_viewcount() throws Exception {
        long id = createEvent(body("상세행사", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));

        mockMvc.perform(get("/api/events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("본문 ![](media:42)"))
                .andExpect(jsonPath("$.viewCount").doesNotExist())
                .andExpect(jsonPath("$.author").doesNotExist());
    }

    @Test
    void detail_unknown_is_404() throws Exception {
        mockMvc.perform(get("/api/events/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void put_full_update_changes_fields_and_bumps_version() throws Exception {
        long id = createEvent(body("원본", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        String update =
                """
                {"title":"수정행사","description":"수정","location":"교육관","startAt":"2026-06-11T09:00:00","endAt":"2026-06-11T10:00:00","allDay":true,"tagIds":[],"version":0}
                """;

        mockMvc.perform(put("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정행사"))
                .andExpect(jsonPath("$.allDay").value(true))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void put_with_stale_version_is_409() throws Exception {
        long id = createEvent(body("원본", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        String v0 =
                """
                {"title":"A","description":"c","location":"본당","startAt":"2026-06-10T10:00:00","endAt":"2026-06-10T11:00:00","allDay":false,"tagIds":[],"version":0}
                """;
        mockMvc.perform(put("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void scalar_patch_bumps_version_and_allows_immediate_next_edit() throws Exception {
        long id = createEvent(body("원본", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        // 스칼라 필드(title) 변경 PATCH: version 0 → 응답 version 1(flush 반영).
        mockMvc.perform(patch("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"1차수정","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("1차수정"))
                .andExpect(jsonPath("$.version").value(1));
        // 응답 version(1)으로 즉시 2차 수정 → 200.
        mockMvc.perform(patch("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"2차수정","version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void tag_only_patch_keeps_version_unchanged() throws Exception {
        long id = createEvent(body("원본", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        // tag-only PATCH(스칼라 불변): events 행 미변경이라 version 유지(설계 §5 Finding 2). tagIds=[]로 동일.
        mockMvc.perform(patch("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[],"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
        // version 0이 여전히 유효 → 동일 version으로 다시 통과.
        mockMvc.perform(patch("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tagIds":[],"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void patch_end_before_start_is_400() throws Exception {
        long id = createEvent(body("원본", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));
        // 기존 start 6/10 10:00보다 이전으로 end만 변경 → 서비스 교차검증 400.
        mockMvc.perform(patch("/api/admin/events/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"endAt":"2026-06-10T09:00:00","version":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void delete_soft_deletes_then_detail_404() throws Exception {
        long id = createEvent(body("삭제대상", "2026-06-10T10:00:00", "2026-06-10T11:00:00", false));

        mockMvc.perform(delete("/api/admin/events/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/events/" + id)).andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인(RED)**

Run: `./gradlew test --tests 'com.elipair.church.domain.event.EventApiTest'`
Expected: 컴파일 실패 — `EventController`/`AdminEventController` 빈 없음(또는 404 라우팅 실패).

- [ ] **Step 3: 공개 컨트롤러 구현**

`src/main/java/com/elipair/church/domain/event/EventController.java`:

```java
package com.elipair.church.domain.event;

import com.elipair.church.domain.event.dto.EventCardResponse;
import com.elipair.church.domain.event.dto.EventDetailResponse;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 일정 공개 조회 API(스펙 §5.6). 비인증 — SecurityConfig anyRequest permitAll. */
@RestController
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @GetMapping("/api/events")
    public Page<EventCardResponse> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long tagId,
            @PageableDefault(size = 10, sort = "startAt", direction = Sort.Direction.ASC) Pageable pageable) {
        DateRange range = DateRange.resolve(year, month, startDate, endDate);
        return service.list(range, tagId, pageable);
    }

    @GetMapping("/api/events/{id}")
    public EventDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
```

> 기본 정렬: `start_at ASC`(스펙 §5.6 "기본 정렬: start_at"). 클라이언트가 `?sort=`로 덮어쓸 수 있는 "기본값"이다. 달력 한 달 분량은 프론트가 `?size=`를 키워 호출한다(설계 §6.1).

- [ ] **Step 4: 관리 컨트롤러 구현**

`src/main/java/com/elipair/church/domain/event/AdminEventController.java`:

```java
package com.elipair.church.domain.event;

import com.elipair.church.domain.event.dto.EventCreateRequest;
import com.elipair.church.domain.event.dto.EventDetailResponse;
import com.elipair.church.domain.event.dto.EventPatchRequest;
import com.elipair.church.domain.event.dto.EventUpdateRequest;
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

/** 일정 관리 API(스펙 §5.6). 전 메서드 EVENT_WRITE. */
@RestController
@PreAuthorize("hasAuthority('EVENT_WRITE')")
public class AdminEventController {

    private final EventService service;

    public AdminEventController(EventService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/events")
    public ResponseEntity<EventDetailResponse> create(@Valid @RequestBody EventCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/api/admin/events/{id}")
    public EventDetailResponse update(@PathVariable Long id, @Valid @RequestBody EventUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/api/admin/events/{id}")
    public EventDetailResponse patch(@PathVariable Long id, @Valid @RequestBody EventPatchRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/api/admin/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
```

- [ ] **Step 5: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.event.EventApiTest'`
Expected: BUILD SUCCESSFUL, 19개 테스트 PASS.

- [ ] **Step 6: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/domain/event/EventController.java \
  src/main/java/com/elipair/church/domain/event/AdminEventController.java \
  src/test/java/com/elipair/church/domain/event/EventApiTest.java
git commit -m "feat : 일정 공개·관리 API 추가 #14"
```

---

## Task 6: 전체 빌드 + 커버리지 검증

- [ ] **Step 1: 전체 빌드(포맷·전 테스트·자르 조립)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. event 5개 테스트 클래스 전부 통과, `MigrationIndexTest` 포함 기존 테스트 회귀 없음.

- [ ] **Step 2: 커버리지 확인(80%+ 목표)**

Run: `./gradlew jacocoTestReport`
Expected: `build/reports/jacoco/test/html/index.html`에서 `domain.event` 패키지 라인 커버리지 ≥ 80%.
(서비스/엔티티/Spec/Provider/DateRange/컨트롤러가 5개 테스트로 모두 실행됨 — notice와 동일 수준.)

- [ ] **Step 3: 최종 정리 커밋(필요 시, 사용자 승인 시)**

> 변경이 남아있을 때만. event 외 파일은 `MigrationIndexTest` 1건 외에 수정하지 않는다.

---

## Self-Review (작성자 점검 완료)

**1. 스펙 커버리지** — 설계 문서 각 절을 태스크에 매핑:
- 설계 §1 결정표(겹침 end_at 배타·작성자 미노출·view_count 없음·q 없음·낙관락 범위·교차검증·정렬) → Task 1(Spec/엔티티), Task 4(서비스 flush/교차검증), Task 5(컨트롤러 정렬/범위), DTO(Task 3 @AssertTrue).
- §2 데이터 모델(V9·엔티티) → Task 1. start_at/end_at/all_day/부분 인덱스 포함.
- §3 리포지토리/Spec(겹침 술어·DateRange) → Task 1. `findReferencesByMedia`·`filter(range, taggedIds)`·`DateRange.resolve`.
- §4 미디어 참조 → Task 2. 경계 안전 정규식·"event" 타입·soft-delete 제외.
- §5 서비스(list/get/create/update/patch/delete, flush 정합, tag-only version 유지, 교차검증) → Task 4.
- §5.1 교차검증(create/update @AssertTrue, patch 서비스) → Task 3(DTO), Task 4(서비스 patch).
- §6 API(공개 range/year-month/start-end·검증 400, 관리 EVENT_WRITE, 기본 정렬 start_at ASC) → Task 5.
- §7 테스트 4종(+DateRangeTest) → Task 1·2·4·5. 부분 인덱스 검증은 `MigrationIndexTest`(Task 1 Step 10).
- 리뷰 반영 3건: Finding 1(겹침 end_at 배타 술어+경계 테스트 — Task 1 `event_ending_exactly_on_month_boundary_is_not_double_counted`, Task 5 `public_list_july_excludes_boundary_event_keeps_multiday`), Finding 2(tag-only version 유지 — Task 5 `tag_only_patch_keeps_version_unchanged`), Finding 3(year 1–9999 — Task 1 `DateRangeTest.out_of_range_year_or_month_is_rejected`).

**2. 플레이스홀더 스캔** — TBD/TODO/"적절히 처리" 없음. 모든 코드 스텝에 완전한 코드 포함.

**3. 타입 일관성** — `Event.create(String,String,String,LocalDateTime,LocalDateTime,boolean)`, `update(동일 6인자)`, `applyPatch(String,String,String,LocalDateTime,LocalDateTime,Boolean)`, `EventService(EventRepository,ContentTagService)`, `list(DateRange,Long,Pageable)`, `DateRange.resolve(Integer,Integer,LocalDate,LocalDate)`·`from()`/`toExclusive()`, `EventSpecifications.filter(DateRange,List<Long>)`, `isAllDay()`(Lombok boolean 게터), `ContentResourceType.EVENT`, `ErrorCode.{INVALID_INPUT_VALUE,RESOURCE_NOT_FOUND,OPTIMISTIC_LOCK_CONFLICT}`, `EventRefRow.getId()/getTitle()` — 태스크 전반 일치. DTO 필드명(`startAt`,`endAt`,`allDay`,`location`,`version`,`tagIds`)이 엔티티·서비스·테스트·JSON 본문에서 동일. EventCardResponse/DetailResponse에 author·viewCount 부재가 서비스 매핑·E2E 검증(`doesNotExist`)과 일치.
