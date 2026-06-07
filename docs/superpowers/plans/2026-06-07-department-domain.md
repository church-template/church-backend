# 교구/부서(Department) 도메인 구현 계획 — 이슈 #15 (D10)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 교구·부서 소개와 자기참조 계층을 등록·조회·관리하는 단일 교회용 `department` 도메인을 추가한다(스펙 §5.8). 공개 목록(비페이징 평배열, `sort_order ASC, id ASC`, `parentId` 포함), 공개 상세, 관리자 CRUD(DEPT_WRITE), 마크다운 본문 + `media:{id}` 참조, 낙관락을 포함한다. 태그·작성자·조회수 미지원.

**Architecture:** Event(D9)/Notice(D8)가 세운 콘텐츠 도메인 패턴을 답습하되 **자기참조 계층**이 고유점. `BaseEntity` 상속, `parentId`는 평문 `Long`, 계층 검증(자기참조·미존재·순환), 차단형 삭제(살아있는 자식 → `409 DEPARTMENT_HAS_CHILDREN`), `sort_order` 미지정 시 `max+10` append(positions 선례), 명시적 `@Version` 비교 + `repository.flush()`, **계층 변경 트랜잭션은 PostgreSQL advisory lock으로 직렬화**(write-skew 사이클·삭제부모-자식 차단), `MediaReferenceProvider` 구현(경계 안전 정규식, `name`→`title`). **태그 없음**(ContentTagService 미사용), **작성자·조회수 미노출**. 목록은 positions/tags처럼 평배열(Page 봉투 아님). 경로 인가는 기존 `SecurityConfig` 3분법으로 충족. 설계 문서: `docs/superpowers/specs/2026-06-07-department-domain-design.md`.

**Tech Stack:** Spring Boot 4.0.6 / Java 21 / Spring Data JPA / PostgreSQL + Flyway / Spring Security(JWT) / Testcontainers / JUnit5 + Mockito + AssertJ / Lombok / Spotless(palantirJavaFormat).

---

## 사전 메모 (실행자 필독)

- **커밋/푸시는 프로젝트 관례상 "요청 시에만"** 한다(`CLAUDE.md`). 각 Task의 커밋 스텝은 사용자가 승인하면 실행한다. 무단 커밋 금지.
- **커밋 메시지 금지사항: `Co-Authored-By` 태그 절대 추가 금지.** 형식은 `<type> : <설명> #15`(콜론 앞 공백, 한글).
- **버전/체인지로그 파일 손대지 말 것**(`version.yml`, `build.gradle` version, `CHANGELOG.*`). 자동화 소유.
- 포맷 검증: `./gradlew build`는 `spotlessCheck`를 포함한다. 포맷 위반 시 **`./gradlew spotlessApply`** 후 다시 빌드한다.
- 모든 신규 코드는 `com.elipair.church.domain.department` 패키지. Event 파일과 거의 1:1 대응되며 차이는 필드 셋·계층(parentId/검증/순환)·차단형 삭제·advisory lock·max+10 sortOrder·태그 없음·작성자 없음·비페이징 평배열이다.
- 확정 사실(검증 완료): `DEPT_WRITE`는 `V2__create_rbac.sql:37`에 시드됨 · `BaseEntity`에 `createdAt`/`updatedAt`/`createdBy`/`updatedBy`/`version`(Long)/`softDelete()`/`isDeleted()` 존재 · `ErrorCode.{INVALID_INPUT_VALUE,RESOURCE_NOT_FOUND,OPTIMISTIC_LOCK_CONFLICT,MEDIA_IN_USE,ROLE_IN_USE}` 존재(`DEPARTMENT_HAS_CHILDREN`은 Task 4에서 신설) · `BusinessException(ErrorCode)` 및 `BusinessException(ErrorCode, String)` 생성자·`getErrorCode()` 존재(`PositionService` 사용) · 다음 마이그레이션 번호는 **V10**(V9=events) · `MediaReferenceProvider.findReferences(long)` SPI 존재(MediaService가 `List<MediaReferenceProvider>` 자동 수집) · `ContentRef(String type, Long id, String title)` 레코드 · `PositionService.create`가 `findMaxSortOrder().map(max -> max + 10).orElse(10)` 패턴 사용 · `PositionCreateRequest`가 `@PositiveOrZero Integer sortOrder` 사용 · `JwtTokenProvider.issueAccess(MemberPrincipal, String position, List<String> permissions)` · `MemberPrincipal(Long id, String uuid, String name, int maxPriority)` · `Member.create(phone, name, password, email, positionId, termsAgreed, privacyAgreed)`.

## File Structure (생성/수정 파일 맵)

**생성 — main**
- `src/main/resources/db/migration/V10__create_departments.sql` — departments 테이블 + 부분 인덱스(자기참조 FK).
- `src/main/java/com/elipair/church/domain/department/Department.java` — 엔티티(BaseEntity 상속, 평문 parentId).
- `src/main/java/com/elipair/church/domain/department/DepartmentRefRow.java` — 참조추적 인터페이스 프로젝션(name→title).
- `src/main/java/com/elipair/church/domain/department/DepartmentRepository.java` — JpaRepository + 파생쿼리 + 참조 네이티브 + advisory lock.
- `src/main/java/com/elipair/church/domain/department/DepartmentReferenceProvider.java` — MediaReferenceProvider 구현.
- `src/main/java/com/elipair/church/domain/department/DepartmentService.java` — 도메인 서비스(계층 검증·차단삭제·락).
- `src/main/java/com/elipair/church/domain/department/DepartmentController.java` — 공개 조회 API(평배열).
- `src/main/java/com/elipair/church/domain/department/AdminDepartmentController.java` — 관리 API(DEPT_WRITE).
- `src/main/java/com/elipair/church/domain/department/dto/DepartmentCreateRequest.java`
- `src/main/java/com/elipair/church/domain/department/dto/DepartmentUpdateRequest.java`
- `src/main/java/com/elipair/church/domain/department/dto/DepartmentPatchRequest.java`
- `src/main/java/com/elipair/church/domain/department/dto/DepartmentCardResponse.java`
- `src/main/java/com/elipair/church/domain/department/dto/DepartmentDetailResponse.java`

**생성 — test**
- `src/test/java/com/elipair/church/domain/department/DepartmentRepositoryTest.java`
- `src/test/java/com/elipair/church/domain/department/DepartmentReferenceProviderTest.java`
- `src/test/java/com/elipair/church/domain/department/DepartmentServiceTest.java`
- `src/test/java/com/elipair/church/domain/department/DepartmentApiTest.java`

**수정**
- `src/main/java/com/elipair/church/global/exception/ErrorCode.java` — `DEPARTMENT_HAS_CHILDREN`(409) 1종 추가 (Task 4).
- `src/test/java/com/elipair/church/MigrationIndexTest.java` — `idx_departments_sort_order` 부분 인덱스 검증 1건 추가 (Task 1).

**수정 없음(확인 완료):** `SecurityConfig`(3분법 충족, `/api/departments/**` 공개·`/api/admin/**` 인증), `GlobalExceptionHandler`(낙관락·BusinessException 매핑 완료), `MediaService`(Provider 자동 수집), `ContentResourceType`(부서는 비태그 — 손대지 않음).

---

## Task 1: 영속성 기반 (V10 + 엔티티 + 리포지토리 + 인덱스 검증)

**Files:**
- Create: `src/main/resources/db/migration/V10__create_departments.sql`
- Create: `src/main/java/com/elipair/church/domain/department/Department.java`
- Create: `src/main/java/com/elipair/church/domain/department/DepartmentRefRow.java`
- Create: `src/main/java/com/elipair/church/domain/department/DepartmentRepository.java`
- Test: `src/test/java/com/elipair/church/domain/department/DepartmentRepositoryTest.java`
- Modify: `src/test/java/com/elipair/church/MigrationIndexTest.java`

- [ ] **Step 1: 실패하는 리포지토리 슬라이스 테스트 작성**

`src/test/java/com/elipair/church/domain/department/DepartmentRepositoryTest.java`:

```java
package com.elipair.church.domain.department;

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
class DepartmentRepositoryTest {

    @Autowired
    private DepartmentRepository repository;

    private Department dept(String name, Long parentId, Integer sortOrder) {
        return Department.create(name, "본문", "김목사", parentId, sortOrder);
    }

    @Test
    void save_populates_audit_columns() {
        Department saved = repository.saveAndFlush(dept("부서", null, 10));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getSortOrder()).isEqualTo(10);
    }

    @Test
    void findByIdAndDeletedAtIsNull_excludes_soft_deleted() {
        Department active = repository.saveAndFlush(dept("활성", null, 10));
        Department deleted = dept("삭제", null, 20);
        deleted.softDelete();
        Department savedDeleted = repository.saveAndFlush(deleted);

        assertThat(repository.findByIdAndDeletedAtIsNull(active.getId())).isPresent();
        assertThat(repository.findByIdAndDeletedAtIsNull(savedDeleted.getId())).isEmpty();
    }

    @Test
    void list_ordered_by_sort_order_then_id_excludes_deleted() {
        repository.saveAndFlush(dept("A", null, 20));
        repository.saveAndFlush(dept("B", null, 10)); // 먼저 저장 → id 작음
        repository.saveAndFlush(dept("C", null, 10)); // B와 동률 → id tie-break로 뒤
        Department del = dept("D", null, 5);
        del.softDelete();
        repository.saveAndFlush(del);

        List<String> names = repository.findByDeletedAtIsNullOrderBySortOrderAscIdAsc().stream()
                .map(Department::getName)
                .toList();
        assertThat(names).containsExactly("B", "C", "A"); // 10(B,id<C), 10(C), 20(A); 삭제(5) 제외
    }

    @Test
    void findMaxSortOrder_empty_when_no_active_rows() {
        assertThat(repository.findMaxSortOrder()).isEmpty();
    }

    @Test
    void findMaxSortOrder_returns_max_over_active_rows() {
        repository.saveAndFlush(dept("A", null, 10));
        repository.saveAndFlush(dept("B", null, 30));
        Department del = dept("D", null, 99);
        del.softDelete();
        repository.saveAndFlush(del);

        assertThat(repository.findMaxSortOrder()).contains(30); // 삭제행(99) 제외
    }

    @Test
    void exists_children_true_and_excludes_deleted_child() {
        Department parent = repository.saveAndFlush(dept("P", null, 10));
        Department child = repository.saveAndFlush(dept("C", parent.getId(), 10));
        assertThat(repository.existsByParentIdAndDeletedAtIsNull(parent.getId())).isTrue();

        child.softDelete();
        repository.saveAndFlush(child);
        assertThat(repository.existsByParentIdAndDeletedAtIsNull(parent.getId())).isFalse();
    }

    @Test
    void findReferencesByMedia_is_boundary_safe_and_maps_name_to_title() {
        repository.saveAndFlush(Department.create("42참조", "본문 ![](media:42) 끝", "김", null, 10));
        repository.saveAndFlush(Department.create("420참조", "본문 ![](media:420) 끝", "김", null, 20));

        List<DepartmentRefRow> rows = repository.findReferencesByMedia("media:42($|[^0-9])");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTitle()).isEqualTo("42참조"); // title = name 별칭
    }

    @Test
    void lockHierarchy_executes_without_error() {
        // pg_advisory_xact_lock 네이티브 호출(반환 void 매핑 포함)이 정상 실행되는지 검증.
        // @DataJpaTest는 트랜잭션이라 lock은 롤백 시 자동 해제된다.
        repository.lockHierarchy(100_015L);
        assertThat(repository.findMaxSortOrder()).isEmpty(); // 호출 후 정상 진행 확인
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인(RED)**

Run: `./gradlew test --tests 'com.elipair.church.domain.department.DepartmentRepositoryTest'`
Expected: 컴파일 실패 — `Department`, `DepartmentRepository`, `DepartmentRefRow` 심볼 없음.

- [ ] **Step 3: 마이그레이션 작성**

`src/main/resources/db/migration/V10__create_departments.sql`:

```sql
-- 교구/부서 콘텐츠(스펙 §5.8). BaseEntity 상속, 감사/소프트삭제/낙관락 컬럼은 V7~V9 관례를 따른다.
-- 본문 description은 마크다운 원본(TEXT), 본문 내 이미지는 media:{id}로 참조(스펙 §5). V9=events 점유 → V10.
-- parent_id는 자기참조 FK(계층). soft delete만 하므로 ON DELETE 절 불필요 — 자식 차단은 앱 레벨(살아있는 자식 검사).
CREATE TABLE departments (
    id          BIGINT       GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    leader      VARCHAR(100),
    parent_id   BIGINT       REFERENCES departments (id),
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,
    created_by  BIGINT       REFERENCES members (id),
    updated_by  BIGINT       REFERENCES members (id),
    deleted_at  TIMESTAMP,
    version     BIGINT       NOT NULL DEFAULT 0
);

-- 기본 정렬 = sort_order, 미삭제만(스펙 §6 부분 인덱스).
CREATE INDEX idx_departments_sort_order ON departments (sort_order) WHERE deleted_at IS NULL;
```

- [ ] **Step 4: 엔티티 작성**

`src/main/java/com/elipair/church/domain/department/Department.java`:

```java
package com.elipair.church.domain.department;

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
 * 교구/부서(스펙 §5.8). 수정가능 콘텐츠라 BaseEntity(감사·소프트삭제·낙관락)를 상속.
 * parentId는 자기참조(평문 Long — created_by/updated_by 관례와 동일, 순환검사는 리포지토리로 체인 탐색).
 * leader는 담당 교역자 이름 평문(FK 아님). created_by/updated_by는 AuditorAware 자동 주입·응답 미노출(설계 §1).
 */
@Entity
@Table(name = "departments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String leader;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    private Department(String name, String description, String leader, Long parentId, Integer sortOrder) {
        this.name = name;
        this.description = description;
        this.leader = leader;
        this.parentId = parentId;
        this.sortOrder = sortOrder;
    }

    /** 팩토리. sortOrder는 서비스가 해석한 값(미지정 시 max+10)을 받는다. */
    public static Department create(String name, String description, String leader, Long parentId, Integer sortOrder) {
        return new Department(name, description, leader, parentId, sortOrder);
    }

    /** PUT 전체 교체 — parentId=null이면 루트화, sortOrder=null이면 기존값 유지(positions update 관례). */
    public void update(String name, String description, String leader, Long parentId, Integer sortOrder) {
        this.name = name;
        this.description = description;
        this.leader = leader;
        this.parentId = parentId;
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }

    /** PATCH 부분 수정 — null 인자는 미변경(parentId 비우기=루트화는 PUT 사용). */
    public void applyPatch(String name, String description, String leader, Long parentId, Integer sortOrder) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (leader != null) {
            this.leader = leader;
        }
        if (parentId != null) {
            this.parentId = parentId;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }
}
```

- [ ] **Step 5: 참조 프로젝션 작성**

`src/main/java/com/elipair/church/domain/department/DepartmentRefRow.java`:

```java
package com.elipair.church.domain.department;

/** 미디어 참조 추적용 인터페이스 프로젝션 — (id, title) 한 행. title은 부서 name 별칭. */
public interface DepartmentRefRow {
    Long getId();

    String getTitle();
}
```

- [ ] **Step 6: 리포지토리 작성**

`src/main/java/com/elipair/church/domain/department/DepartmentRepository.java`:

```java
package com.elipair.church.domain.department;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByIdAndDeletedAtIsNull(Long id);

    /** 공개 목록 — 미삭제만, sort_order ASC, 동률 시 id ASC(결정적 정렬). */
    List<Department> findByDeletedAtIsNullOrderBySortOrderAscIdAsc();

    /** 삭제 차단용 — 살아있는 자식 존재 여부(soft-deleted 자식 제외). */
    boolean existsByParentIdAndDeletedAtIsNull(Long parentId);

    /** create append 기준값(미삭제 행 최대 sort_order). 없으면 서비스가 10으로 시작. */
    @Query("select max(d.sortOrder) from Department d where d.deletedAt is null")
    Optional<Integer> findMaxSortOrder();

    /**
     * 본문(description)이 media:{id}를 참조하는 미삭제 부서(id·name→title). PG 정규식 ~ 로 경계 안전 매칭.
     * pattern 예: "media:42($|[^0-9])" — 42가 media:420/421에 매칭되지 않는다.
     */
    @Query(
            value = "select id as id, name as title from departments where deleted_at is null and description ~ :pattern",
            nativeQuery = true)
    List<DepartmentRefRow> findReferencesByMedia(@Param("pattern") String pattern);

    /**
     * 계층 변경 트랜잭션 직렬화(설계 §5.3). pg_advisory_xact_lock은 트랜잭션 종료 시 자동 해제(수동 unlock 불필요).
     * 동시 reparent(write-skew 사이클)·delete-parent vs create-child 경합을 단일 락으로 막는다.
     */
    @Query(value = "select pg_advisory_xact_lock(:key)", nativeQuery = true)
    void lockHierarchy(@Param("key") long key);
}
```

> 참고: `JpaSpecificationExecutor`는 상속하지 않는다 — 부서는 동적 범위/태그 필터가 없어 파생 쿼리로 충분(Event와 다른 점).

- [ ] **Step 7: MigrationIndexTest에 departments 인덱스 검증 추가**

`src/test/java/com/elipair/church/MigrationIndexTest.java` — 기존 `events_start_at_is_partial_on_active_rows` 테스트 메서드 **다음에** 아래 메서드를 추가한다(클래스 닫는 `}` 직전):

```java
    @Test
    void departments_sort_order_is_partial_on_active_rows() {
        assertThat(indexDef("idx_departments_sort_order"))
                .as("V10 부서 정렬 인덱스")
                .isNotNull()
                .contains("sort_order")
                .contains("deleted_at IS NULL");
    }
```

- [ ] **Step 8: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.department.DepartmentRepositoryTest' --tests 'com.elipair.church.MigrationIndexTest'`
Expected: BUILD SUCCESSFUL. DepartmentRepositoryTest 8 PASS(포함: `lockHierarchy_executes_without_error` — advisory lock 네이티브쿼리 검증), MigrationIndexTest 5 PASS(기존 4 + departments 1).

- [ ] **Step 9: 커밋(사용자 승인 시)**

```bash
git add src/main/resources/db/migration/V10__create_departments.sql \
  src/main/java/com/elipair/church/domain/department/Department.java \
  src/main/java/com/elipair/church/domain/department/DepartmentRefRow.java \
  src/main/java/com/elipair/church/domain/department/DepartmentRepository.java \
  src/test/java/com/elipair/church/domain/department/DepartmentRepositoryTest.java \
  src/test/java/com/elipair/church/MigrationIndexTest.java
git commit -m "feat : 부서 엔티티·리포지토리·계층락·V10 마이그레이션 추가 #15"
```

---

## Task 2: 미디어 참조 추적 (DepartmentReferenceProvider)

**Files:**
- Create: `src/main/java/com/elipair/church/domain/department/DepartmentReferenceProvider.java`
- Test: `src/test/java/com/elipair/church/domain/department/DepartmentReferenceProviderTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/elipair/church/domain/department/DepartmentReferenceProviderTest.java`:

```java
package com.elipair.church.domain.department;

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
class DepartmentReferenceProviderTest {

    @Autowired
    private DepartmentRepository repository;

    private DepartmentReferenceProvider provider;

    @BeforeEach
    void init() {
        provider = new DepartmentReferenceProvider(repository);
    }

    private Department withBody(String name, String body) {
        return Department.create(name, body, "김목사", null, 10);
    }

    @Test
    void matches_exact_id_not_prefix_collision() {
        repository.saveAndFlush(withBody("42참조", "본문 ![](media:42) 끝"));
        repository.saveAndFlush(withBody("420참조", "본문 ![](media:420) 끝"));

        List<ContentRef> refs = provider.findReferences(42);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).type()).isEqualTo("department");
        assertThat(refs.get(0).title()).isEqualTo("42참조"); // title = name
    }

    @Test
    void matches_when_id_at_end_of_body() {
        repository.saveAndFlush(withBody("끝참조", "마지막 이미지 media:7"));

        assertThat(provider.findReferences(7)).hasSize(1);
    }

    @Test
    void excludes_soft_deleted() {
        Department deleted = withBody("삭제", "![](media:9)");
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

Run: `./gradlew test --tests 'com.elipair.church.domain.department.DepartmentReferenceProviderTest'`
Expected: 컴파일 실패 — `DepartmentReferenceProvider` 심볼 없음.

- [ ] **Step 3: Provider 구현**

`src/main/java/com/elipair/church/domain/department/DepartmentReferenceProvider.java`:

```java
package com.elipair.church.domain.department;

import com.elipair.church.domain.media.MediaReferenceProvider;
import com.elipair.church.global.common.ContentRef;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 본문(description) media:{id} 참조 추적(스펙 §5.10 SPI). MediaService가 빈으로 주입받아 합집합에 더한다.
 * ContentRef.type은 소문자 "department" — 미디어 참조 API 계약 값(스펙 §5.10 UNION). title은 부서 name.
 * soft-deleted 부서는 제외(자기 치유). 경계 안전: media:42 뒤에 숫자가 오면 매칭하지 않아 420/421에 오탐되지 않는다.
 */
@Component
class DepartmentReferenceProvider implements MediaReferenceProvider {

    private final DepartmentRepository repository;

    DepartmentReferenceProvider(DepartmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ContentRef> findReferences(long mediaId) {
        String pattern = "media:" + mediaId + "($|[^0-9])";
        return repository.findReferencesByMedia(pattern).stream()
                .map(row -> new ContentRef("department", row.getId(), row.getTitle()))
                .toList();
    }
}
```

- [ ] **Step 4: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.department.DepartmentReferenceProviderTest'`
Expected: BUILD SUCCESSFUL, 4개 테스트 PASS.

- [ ] **Step 5: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/domain/department/DepartmentReferenceProvider.java \
  src/test/java/com/elipair/church/domain/department/DepartmentReferenceProviderTest.java
git commit -m "feat : 부서 미디어 참조추적 Provider 추가 #15"
```

---

## Task 3: DTO 5종

**Files:**
- Create: `src/main/java/com/elipair/church/domain/department/dto/DepartmentCreateRequest.java`
- Create: `src/main/java/com/elipair/church/domain/department/dto/DepartmentUpdateRequest.java`
- Create: `src/main/java/com/elipair/church/domain/department/dto/DepartmentPatchRequest.java`
- Create: `src/main/java/com/elipair/church/domain/department/dto/DepartmentCardResponse.java`
- Create: `src/main/java/com/elipair/church/domain/department/dto/DepartmentDetailResponse.java`

> DTO는 다음 Task의 서비스/컨트롤러 컴파일에 필요하므로 여기서 먼저 만든다. 테스트는 Task 4·5에서 이들을 사용한다.

- [ ] **Step 1: DepartmentCreateRequest**

`src/main/java/com/elipair/church/domain/department/dto/DepartmentCreateRequest.java`:

```java
package com.elipair.church.domain.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 부서 등록(POST). @Size(max)는 V10 컬럼 길이와 일치(description은 TEXT지만 스펙 §5 최소검증 상한).
 * parentId nullable=루트. sortOrder 미지정 시 서비스가 max+10 append. sortOrder는 음수 불가(@PositiveOrZero, positions 선례).
 */
public record DepartmentCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50000) String description,
        @Size(max = 100) String leader,
        Long parentId,
        @PositiveOrZero Integer sortOrder) {}
```

- [ ] **Step 2: DepartmentUpdateRequest**

`src/main/java/com/elipair/church/domain/department/dto/DepartmentUpdateRequest.java`:

```java
package com.elipair.church.domain.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 부서 전체 수정(PUT). version 낙관락 비교용 필수. parentId=null이면 루트화, sortOrder=null이면 기존값 유지. */
public record DepartmentUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50000) String description,
        @Size(max = 100) String leader,
        Long parentId,
        @PositiveOrZero Integer sortOrder,
        @NotNull Long version) {}
```

- [ ] **Step 3: DepartmentPatchRequest**

`src/main/java/com/elipair/church/domain/department/dto/DepartmentPatchRequest.java`:

```java
package com.elipair.church.domain.department.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 부서 부분 수정(PATCH). 전달된(비-null) 필드만 적용. parentId=null·sortOrder=null은 미변경(루트화는 PUT). version 필수.
 */
public record DepartmentPatchRequest(
        @Size(max = 100) String name,
        @Size(max = 50000) String description,
        @Size(max = 100) String leader,
        Long parentId,
        @PositiveOrZero Integer sortOrder,
        @NotNull Long version) {}
```

- [ ] **Step 4: DepartmentCardResponse**

`src/main/java/com/elipair/church/domain/department/dto/DepartmentCardResponse.java`:

```java
package com.elipair.church.domain.department.dto;

/** 부서 목록 카드(스펙 §5.8). description(본문)·author 제외 — 메타만(이름·담당·상위·정렬). 프론트가 parentId로 트리 조립. */
public record DepartmentCardResponse(Long id, String name, String leader, Long parentId, Integer sortOrder) {}
```

- [ ] **Step 5: DepartmentDetailResponse**

`src/main/java/com/elipair/church/domain/department/dto/DepartmentDetailResponse.java`:

```java
package com.elipair.church.domain.department.dto;

import java.time.LocalDateTime;

/** 부서 상세(스펙 §5.8). description·version 포함(version은 편집 재전송용 — 수정 응답은 flush로 post-increment). author 없음(설계 §1). */
public record DepartmentDetailResponse(
        Long id,
        String name,
        String description,
        String leader,
        Long parentId,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version) {}
```

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew spotlessApply && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/domain/department/dto/
git commit -m "feat : 부서 요청·응답 DTO 추가 #15"
```

---

## Task 4: ErrorCode 추가 + 서비스 (DepartmentService)

**Files:**
- Modify: `src/main/java/com/elipair/church/global/exception/ErrorCode.java`
- Create: `src/main/java/com/elipair/church/domain/department/DepartmentService.java`
- Test: `src/test/java/com/elipair/church/domain/department/DepartmentServiceTest.java`

- [ ] **Step 1: ErrorCode에 DEPARTMENT_HAS_CHILDREN 추가**

`src/main/java/com/elipair/church/global/exception/ErrorCode.java` — 기존 `ROLE_IN_USE(...)` 줄 **다음에** 아래 한 줄을 추가한다(enum 상수 목록 안, `FILE_SIZE_EXCEEDED` 앞):

```java
    DEPARTMENT_HAS_CHILDREN(HttpStatus.CONFLICT, "DEPARTMENT_HAS_CHILDREN", "하위 부서가 있어 삭제할 수 없습니다"),
```

> `MEDIA_IN_USE`·`ROLE_IN_USE`와 평행한 도메인 고유 409. parent 검증 3종(미존재·자기참조·순환)은 기존 `INVALID_INPUT_VALUE` 재사용 — 신규 코드는 이 1종뿐.

- [ ] **Step 2: 실패하는 단위 테스트 작성**

`src/test/java/com/elipair/church/domain/department/DepartmentServiceTest.java`:

```java
package com.elipair.church.domain.department;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.department.dto.DepartmentCreateRequest;
import com.elipair.church.domain.department.dto.DepartmentDetailResponse;
import com.elipair.church.domain.department.dto.DepartmentPatchRequest;
import com.elipair.church.domain.department.dto.DepartmentUpdateRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DepartmentServiceTest {

    private DepartmentRepository repository;
    private DepartmentService service;

    @BeforeEach
    void init() {
        repository = mock(DepartmentRepository.class);
        service = new DepartmentService(repository);
    }

    private Department mockDept(long id, long version) {
        Department d = mock(Department.class);
        when(d.getId()).thenReturn(id);
        when(d.getVersion()).thenReturn(version);
        return d;
    }

    @Test
    void create_root_appends_first_sort_order_and_skips_lock() {
        when(repository.findMaxSortOrder()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DepartmentDetailResponse res =
                service.create(new DepartmentCreateRequest("예배부", "본문", "김목사", null, null));

        assertThat(res.sortOrder()).isEqualTo(10); // 빈 테이블 → 10
        assertThat(res.parentId()).isNull();
        verify(repository, never()).lockHierarchy(anyLong()); // 루트 생성은 구조 위험 없음 → 락 안 잡음
    }

    @Test
    void create_with_parent_appends_max_plus_10_and_locks() {
        Department parent = Department.create("상위", "본문", "목사", null, 10); // 루트(parentId null)
        when(repository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(parent));
        when(repository.findMaxSortOrder()).thenReturn(Optional.of(20));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DepartmentDetailResponse res =
                service.create(new DepartmentCreateRequest("성가대", "본문", "이집사", 5L, null));

        assertThat(res.sortOrder()).isEqualTo(30); // 20 + 10
        assertThat(res.parentId()).isEqualTo(5L);
        verify(repository).lockHierarchy(anyLong()); // 부모 지정 → 락
    }

    @Test
    void create_with_explicit_sort_order_skips_append() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DepartmentDetailResponse res =
                service.create(new DepartmentCreateRequest("부서", "본문", "목사", null, 99));

        assertThat(res.sortOrder()).isEqualTo(99);
        verify(repository, never()).findMaxSortOrder(); // 명시값이라 append 조회 안 함
    }

    @Test
    void create_with_nonexistent_parent_is_400() {
        when(repository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new DepartmentCreateRequest("x", null, null, 99L, null)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(repository, never()).save(any());
    }

    @Test
    void update_with_matching_version_updates_locks_and_flushes() {
        Department d = mockDept(10L, 3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        DepartmentUpdateRequest req = new DepartmentUpdateRequest("새이름", "새본문", "새교역자", null, 5, 3L);

        service.update(10L, req);

        verify(repository).lockHierarchy(anyLong());
        verify(d).update("새이름", "새본문", "새교역자", null, 5);
        verify(repository).flush();
    }

    @Test
    void update_with_stale_version_throws_409_and_skips_changes() {
        Department d = mockDept(10L, 3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        DepartmentUpdateRequest req = new DepartmentUpdateRequest("새이름", "새본문", "새교역자", null, 5, 2L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
        verify(d, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void update_self_reference_is_400() {
        Department d = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        DepartmentUpdateRequest req = new DepartmentUpdateRequest("x", null, null, 10L, null, 0L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(d, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void update_descendant_as_parent_is_cycle_400() {
        // self=10(예배부). 후보 부모 20(성가대)의 parentId=10 → 10의 후손 → 사이클.
        Department self = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(self));
        Department twenty = mock(Department.class);
        when(twenty.getParentId()).thenReturn(10L);
        when(repository.findByIdAndDeletedAtIsNull(20L)).thenReturn(Optional.of(twenty));
        DepartmentUpdateRequest req = new DepartmentUpdateRequest("x", null, null, 20L, null, 0L);

        assertThatThrownBy(() -> service.update(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(self, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void patch_with_null_parent_keeps_parent_and_skips_lock() {
        Department d = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        DepartmentPatchRequest req = new DepartmentPatchRequest("새이름", null, null, null, null, 0L);

        service.patch(10L, req);

        verify(repository, never()).lockHierarchy(anyLong()); // parentId null → 락 안 잡음
        verify(d).applyPatch("새이름", null, null, null, null);
        verify(repository).flush();
    }

    @Test
    void patch_with_parent_locks_and_validates() {
        Department d = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        Department parent = Department.create("상위", "본문", "목사", null, 10); // 루트
        when(repository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(parent));
        DepartmentPatchRequest req = new DepartmentPatchRequest(null, null, null, 7L, null, 0L);

        service.patch(10L, req);

        verify(repository).lockHierarchy(anyLong());
        verify(d).applyPatch(null, null, null, 7L, null);
        verify(repository).flush();
    }

    @Test
    void patch_with_stale_version_throws_409() {
        Department d = mockDept(10L, 3L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        DepartmentPatchRequest req = new DepartmentPatchRequest("x", null, null, null, null, 2L);

        assertThatThrownBy(() -> service.patch(10L, req))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    void delete_with_live_children_throws_409_and_skips_soft_delete() {
        Department d = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        when(repository.existsByParentIdAndDeletedAtIsNull(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(ErrorCode.DEPARTMENT_HAS_CHILDREN));
        verify(d, never()).softDelete();
        verify(repository).lockHierarchy(anyLong());
    }

    @Test
    void delete_without_children_soft_deletes() {
        Department d = mockDept(10L, 0L);
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));
        when(repository.existsByParentIdAndDeletedAtIsNull(10L)).thenReturn(false);

        service.delete(10L);

        verify(d).softDelete();
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
        Department d = mockDept(10L, 0L);
        when(d.getName()).thenReturn("예배부");
        when(repository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(d));

        assertThat(service.get(10L).name()).isEqualTo("예배부");
    }

    @Test
    void list_returns_cards_without_description() {
        Department d = mock(Department.class);
        when(d.getId()).thenReturn(1L);
        when(d.getName()).thenReturn("예배부");
        when(d.getLeader()).thenReturn("김목사");
        when(d.getParentId()).thenReturn(null);
        when(d.getSortOrder()).thenReturn(10);
        when(repository.findByDeletedAtIsNullOrderBySortOrderAscIdAsc()).thenReturn(List.of(d));

        assertThat(service.list()).singleElement().satisfies(card -> {
            assertThat(card.name()).isEqualTo("예배부");
            assertThat(card.parentId()).isNull();
            assertThat(card.sortOrder()).isEqualTo(10);
        });
    }
}
```

- [ ] **Step 3: 컴파일 실패 확인(RED)**

Run: `./gradlew test --tests 'com.elipair.church.domain.department.DepartmentServiceTest'`
Expected: 컴파일 실패 — `DepartmentService` 심볼 없음.

- [ ] **Step 4: 서비스 구현**

`src/main/java/com/elipair/church/domain/department/DepartmentService.java`:

```java
package com.elipair.church.domain.department;

import com.elipair.church.domain.department.dto.DepartmentCardResponse;
import com.elipair.church.domain.department.dto.DepartmentCreateRequest;
import com.elipair.church.domain.department.dto.DepartmentDetailResponse;
import com.elipair.church.domain.department.dto.DepartmentPatchRequest;
import com.elipair.church.domain.department.dto.DepartmentUpdateRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부서 서비스(스펙 §5.8). 계층(parentId 자기참조)·차단형 삭제·낙관락. 태그/작성자/조회수 없음(설계 §1).
 * 계층 변경(create-with-parent/update/patch-with-parent/delete)은 advisory lock으로 직렬화해
 * 동시 write-skew(사이클·삭제부모-자식)를 차단한다(설계 §5.3). 낙관락은 명시적 version 비교 + flush로 응답 version 정합.
 */
@Service
@Transactional(readOnly = true)
public class DepartmentService {

    /** 계층 변경 직렬화용 고정 advisory lock 키(설계 §5.3). 부서(#15) 전용 임의 상수. */
    private static final long HIERARCHY_LOCK_KEY = 100_015L;
    /** sort_order 미지정 시 append 간격(positions 선례). */
    private static final int SORT_ORDER_GAP = 10;

    private final DepartmentRepository repository;

    public DepartmentService(DepartmentRepository repository) {
        this.repository = repository;
    }

    public List<DepartmentCardResponse> list() {
        return repository.findByDeletedAtIsNullOrderBySortOrderAscIdAsc().stream()
                .map(d -> new DepartmentCardResponse(
                        d.getId(), d.getName(), d.getLeader(), d.getParentId(), d.getSortOrder()))
                .toList();
    }

    public DepartmentDetailResponse get(Long id) {
        return detail(load(id));
    }

    @Transactional
    public DepartmentDetailResponse create(DepartmentCreateRequest req) {
        if (req.parentId() != null) {
            repository.lockHierarchy(HIERARCHY_LOCK_KEY); // 부모 검증/삭제 경합 직렬화(설계 §5.3)
        }
        validateParent(null, req.parentId());
        int sortOrder = req.sortOrder() != null
                ? req.sortOrder()
                : repository.findMaxSortOrder().map(max -> max + SORT_ORDER_GAP).orElse(SORT_ORDER_GAP);
        Department saved = repository.save(
                Department.create(req.name(), req.description(), req.leader(), req.parentId(), sortOrder));
        return detail(saved);
    }

    @Transactional
    public DepartmentDetailResponse update(Long id, DepartmentUpdateRequest req) {
        repository.lockHierarchy(HIERARCHY_LOCK_KEY); // PUT은 항상 parentId 세팅 → reparent 가능(설계 §5.3)
        Department dept = load(id);
        checkVersion(dept, req.version());
        validateParent(id, req.parentId());
        dept.update(req.name(), req.description(), req.leader(), req.parentId(), req.sortOrder());
        repository.flush(); // 엔티티 필드 변경분의 버전 UPDATE 즉시 반영(응답 version 정합)
        return detail(dept);
    }

    @Transactional
    public DepartmentDetailResponse patch(Long id, DepartmentPatchRequest req) {
        if (req.parentId() != null) {
            repository.lockHierarchy(HIERARCHY_LOCK_KEY); // 부모 변경 시에만 직렬화(설계 §5.3)
        }
        Department dept = load(id);
        checkVersion(dept, req.version());
        if (req.parentId() != null) {
            validateParent(id, req.parentId());
        }
        dept.applyPatch(req.name(), req.description(), req.leader(), req.parentId(), req.sortOrder());
        repository.flush();
        return detail(dept);
    }

    @Transactional
    public void delete(Long id) {
        repository.lockHierarchy(HIERARCHY_LOCK_KEY); // 자식 존재 검사 vs 동시 create-child 경합 직렬화(설계 §5.3)
        Department dept = load(id);
        if (repository.existsByParentIdAndDeletedAtIsNull(id)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_HAS_CHILDREN);
        }
        dept.softDelete();
    }

    private Department load(Long id) {
        return repository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void checkVersion(Department dept, Long expected) {
        if (!dept.getVersion().equals(expected)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
        }
    }

    /**
     * parent 존재·미삭제·자기참조·순환 검증(설계 §5.1). parentId=null이면 즉시 통과.
     * 순환: newParent의 parent_id 체인을 루트까지 상향 탐색하다 selfId를 만나면 거부(트리라 부모 1개, O(depth)).
     */
    private void validateParent(Long selfId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "자기 자신을 상위 부서로 지정할 수 없습니다");
        }
        Department parent = repository
                .findByIdAndDeletedAtIsNull(parentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "존재하지 않는 상위 부서입니다"));
        if (selfId != null) {
            Set<Long> visited = new HashSet<>();
            Long ancestor = parent.getParentId();
            while (ancestor != null && visited.add(ancestor)) { // visited는 방어적 무한루프 차단
                if (ancestor.equals(selfId)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "하위 부서를 상위 부서로 지정할 수 없습니다");
                }
                ancestor = repository
                        .findByIdAndDeletedAtIsNull(ancestor)
                        .map(Department::getParentId)
                        .orElse(null);
            }
        }
    }

    private DepartmentDetailResponse detail(Department d) {
        return new DepartmentDetailResponse(
                d.getId(),
                d.getName(),
                d.getDescription(),
                d.getLeader(),
                d.getParentId(),
                d.getSortOrder(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                d.getVersion());
    }
}
```

- [ ] **Step 5: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.department.DepartmentServiceTest'`
Expected: BUILD SUCCESSFUL, 16개 테스트 PASS.

- [ ] **Step 6: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/global/exception/ErrorCode.java \
  src/main/java/com/elipair/church/domain/department/DepartmentService.java \
  src/test/java/com/elipair/church/domain/department/DepartmentServiceTest.java
git commit -m "feat : 부서 서비스 추가(계층검증·순환차단·차단삭제·계층락·낙관락) #15"
```

---

## Task 5: 컨트롤러 + E2E API 테스트

**Files:**
- Create: `src/main/java/com/elipair/church/domain/department/DepartmentController.java`
- Create: `src/main/java/com/elipair/church/domain/department/AdminDepartmentController.java`
- Test: `src/test/java/com/elipair/church/domain/department/DepartmentApiTest.java`

- [ ] **Step 1: 실패하는 E2E 테스트 작성**

`src/test/java/com/elipair/church/domain/department/DepartmentApiTest.java`:

```java
package com.elipair.church.domain.department;

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
class DepartmentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private DepartmentRepository departmentRepository;

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
        departmentRepository.deleteAll();
        memberRepository.deleteAll(memberRepository.findAll());
    }

    private String token(Long memberId, String permission) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(memberId, "uuid-" + memberId, "관리자", 1000), null, List.of(permission));
    }

    private String adminToken() {
        return token(authorId, "DEPT_WRITE");
    }

    private String body(String name, Long parentId, Integer sortOrder) {
        String p = parentId == null ? "null" : parentId.toString();
        String s = sortOrder == null ? "null" : sortOrder.toString();
        return """
                {"name":"%s","description":"본문 ![](media:42)","leader":"김목사","parentId":%s,"sortOrder":%s}
                """
                .formatted(name, p, s);
    }

    private long createDept(String name, Long parentId, Integer sortOrder) throws Exception {
        String json = mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, parentId, sortOrder)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void create_as_dept_write_returns_201_without_author() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("예배부", null, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("예배부"))
                .andExpect(jsonPath("$.leader").value("김목사"))
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.sortOrder").value(10))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.author").doesNotExist());
    }

    @Test
    void create_anonymous_is_401() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, 10)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void create_without_permission_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", token(authorId, "MEDIA_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, 10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_blank_name_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", null, 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_negative_sort_order_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("음수", null, -1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_without_sort_order_appends_max_plus_10() throws Exception {
        // 첫 건 → 10, 둘째 건 → 20(max+10).
        String first = mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("first", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(10))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assert first != null;

        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("second", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(20));
    }

    @Test
    void create_under_parent_sets_parent_id() throws Exception {
        long parent = createDept("상위", null, 10);

        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("자식", parent, 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value((int) parent));
    }

    @Test
    void create_with_nonexistent_parent_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("고아", 999999L, 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.detail").value("존재하지 않는 상위 부서입니다"));
    }

    @Test
    void create_under_soft_deleted_parent_is_400() throws Exception {
        // FK상 행은 존재하나 deleted_at이 차 있는 부모 밑으로 생성하면 거부(findByIdAndDeletedAtIsNull 가드).
        long parent = createDept("삭제될상위", null, 10);
        mockMvc.perform(delete("/api/admin/departments/" + parent).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("자식", parent, 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.detail").value("존재하지 않는 상위 부서입니다"));
    }

    @Test
    void public_list_is_flat_array_ordered_and_omits_description() throws Exception {
        createDept("나중", null, 20);
        createDept("먼저", null, 10);

        // 최상위 JSON 배열(Page 봉투 아님), sort_order ASC, 카드에 description 없음.
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("먼저"))
                .andExpect(jsonPath("$[1].name").value("나중"))
                .andExpect(jsonPath("$[0].description").doesNotExist())
                .andExpect(jsonPath("$.page").doesNotExist());
    }

    @Test
    void detail_returns_description() throws Exception {
        long id = createDept("상세부서", null, 10);

        mockMvc.perform(get("/api/departments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("본문 ![](media:42)"))
                .andExpect(jsonPath("$.author").doesNotExist());
    }

    @Test
    void detail_unknown_is_404() throws Exception {
        mockMvc.perform(get("/api/departments/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void put_full_update_changes_fields_and_bumps_version() throws Exception {
        long id = createDept("원본", null, 10);
        String update =
                """
                {"name":"수정부서","description":"수정","leader":"이목사","parentId":null,"sortOrder":30,"version":0}
                """;

        mockMvc.perform(put("/api/admin/departments/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정부서"))
                .andExpect(jsonPath("$.sortOrder").value(30))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void put_with_stale_version_is_409() throws Exception {
        long id = createDept("원본", null, 10);
        String v0 =
                """
                {"name":"A","description":"c","leader":"l","parentId":null,"sortOrder":10,"version":0}
                """;
        mockMvc.perform(put("/api/admin/departments/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/departments/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(v0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void patch_changes_parent() throws Exception {
        long parent = createDept("상위", null, 10);
        long child = createDept("이동대상", null, 20);

        mockMvc.perform(patch("/api/admin/departments/" + child)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":%d,"version":0}
                                """.formatted(parent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value((int) parent))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void patch_self_reference_is_400() throws Exception {
        long id = createDept("자기참조", null, 10);

        mockMvc.perform(patch("/api/admin/departments/" + id)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":%d,"version":0}
                                """.formatted(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.detail").value("자기 자신을 상위 부서로 지정할 수 없습니다"));
    }

    @Test
    void patch_descendant_as_parent_is_cycle_400() throws Exception {
        long a = createDept("A", null, 10); // 루트
        long b = createDept("B", a, 10); // b.parent = a

        // a.parent = b 로 바꾸면 b는 a의 후손 → 사이클.
        mockMvc.perform(patch("/api/admin/departments/" + a)
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"parentId":%d,"version":0}
                                """.formatted(b)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.detail").value("하위 부서를 상위 부서로 지정할 수 없습니다"));
    }

    @Test
    void delete_with_children_is_409() throws Exception {
        long parent = createDept("상위", null, 10);
        createDept("자식", parent, 10);

        mockMvc.perform(delete("/api/admin/departments/" + parent).header("Authorization", adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEPARTMENT_HAS_CHILDREN"));
    }

    @Test
    void delete_without_children_then_detail_404() throws Exception {
        long id = createDept("삭제대상", null, 10);

        mockMvc.perform(delete("/api/admin/departments/" + id).header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/departments/" + id)).andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인(RED)**

Run: `./gradlew test --tests 'com.elipair.church.domain.department.DepartmentApiTest'`
Expected: 컴파일 실패 — `DepartmentController`/`AdminDepartmentController` 빈 없음(또는 404 라우팅 실패).

- [ ] **Step 3: 공개 컨트롤러 구현**

`src/main/java/com/elipair/church/domain/department/DepartmentController.java`:

```java
package com.elipair.church.domain.department;

import com.elipair.church.domain.department.dto.DepartmentCardResponse;
import com.elipair.church.domain.department.dto.DepartmentDetailResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 부서 공개 조회 API(스펙 §5.8). 비인증 — SecurityConfig anyRequest permitAll. 목록은 비페이징 평배열(positions/tags와 동일). */
@RestController
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping("/api/departments")
    public List<DepartmentCardResponse> list() {
        return service.list();
    }

    @GetMapping("/api/departments/{id}")
    public DepartmentDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
```

- [ ] **Step 4: 관리 컨트롤러 구현**

`src/main/java/com/elipair/church/domain/department/AdminDepartmentController.java`:

```java
package com.elipair.church.domain.department;

import com.elipair.church.domain.department.dto.DepartmentCreateRequest;
import com.elipair.church.domain.department.dto.DepartmentDetailResponse;
import com.elipair.church.domain.department.dto.DepartmentPatchRequest;
import com.elipair.church.domain.department.dto.DepartmentUpdateRequest;
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

/** 부서 관리 API(스펙 §5.8). 전 메서드 DEPT_WRITE. */
@RestController
@PreAuthorize("hasAuthority('DEPT_WRITE')")
public class AdminDepartmentController {

    private final DepartmentService service;

    public AdminDepartmentController(DepartmentService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/departments")
    public ResponseEntity<DepartmentDetailResponse> create(@Valid @RequestBody DepartmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/api/admin/departments/{id}")
    public DepartmentDetailResponse update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/api/admin/departments/{id}")
    public DepartmentDetailResponse patch(@PathVariable Long id, @Valid @RequestBody DepartmentPatchRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/api/admin/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
```

- [ ] **Step 5: 포맷 적용 후 테스트 통과 확인(GREEN)**

Run: `./gradlew spotlessApply && ./gradlew test --tests 'com.elipair.church.domain.department.DepartmentApiTest'`
Expected: BUILD SUCCESSFUL, 19개 테스트 PASS.

- [ ] **Step 6: 커밋(사용자 승인 시)**

```bash
git add src/main/java/com/elipair/church/domain/department/DepartmentController.java \
  src/main/java/com/elipair/church/domain/department/AdminDepartmentController.java \
  src/test/java/com/elipair/church/domain/department/DepartmentApiTest.java
git commit -m "feat : 부서 공개·관리 API 추가 #15"
```

---

## Task 6: 전체 빌드 + 커버리지 검증

- [ ] **Step 1: 전체 빌드(포맷·전 테스트·자르 조립)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. department 4개 테스트 클래스 전부 통과, `MigrationIndexTest` 포함 기존 테스트 회귀 없음.

- [ ] **Step 2: 커버리지 확인(80%+ 목표)**

Run: `./gradlew jacocoTestReport`
Expected: `build/reports/jacoco/test/html/index.html`에서 `domain.department` 패키지 라인 커버리지 ≥ 80%.
(서비스/엔티티/Provider/컨트롤러가 4개 테스트로 모두 실행됨 — event와 동일 수준.)

- [ ] **Step 3: 최종 정리 커밋(필요 시, 사용자 승인 시)**

> 변경이 남아있을 때만. department 외 파일은 `ErrorCode`·`MigrationIndexTest` 2건 외에 수정하지 않는다.

---

## Self-Review (작성자 점검 완료)

**1. 스펙 커버리지** — 설계 문서 각 절을 태스크에 매핑:
- 설계 §1 결정표(평면+parentId·차단삭제·무제한깊이+순환차단·400 매핑·작성자 미노출·leader 평문·sortOrder max+10·낙관락 범위·경계안전 매칭·PATCH parentId=null 미변경) → Task 1(엔티티/리포지토리), Task 4(서비스 검증/락/append), Task 5(컨트롤러 평배열·DTO @PositiveOrZero).
- §2 데이터 모델(V10·엔티티·평문 parentId) → Task 1. parent_id 자기참조 FK·부분 인덱스 포함.
- §3 리포지토리(파생쿼리·findMaxSortOrder·exists·참조네이티브·lockHierarchy) → Task 1.
- §4 미디어 참조(name→title·"department"·경계안전·soft-delete 제외) → Task 2.
- §5 서비스(list/get/create/update/patch/delete, flush 정합, sortOrder append, parent 검증) → Task 4.
- §5.1 validateParent(자기참조·미존재·순환 상향탐색) → Task 4(서비스 + 단위 테스트 self/cycle/not-found).
- §5.2 신규 에러코드 DEPARTMENT_HAS_CHILDREN → Task 4 Step 1.
- §5.3 advisory lock(create-with-parent/update/patch-with-parent/delete 직렬화) → Task 4(서비스 lockHierarchy 호출 + 단위 verify), Task 1(리포지토리 메서드 + 네이티브쿼리 실행 검증).
- §6 API(공개 평배열 목록·상세, 관리 DEPT_WRITE, 에러 매핑 표) → Task 5.
- §7 테스트 4종 + MigrationIndexTest 1건 → Task 1·2·4·5.
- 리뷰 반영 4건: Finding 1(advisory lock — Task 1 `lockHierarchy_executes_without_error`, Task 4 서비스 lock 호출 + `*_locks_*` verify), Finding 2(max+10 append + id tie-break — Task 1 `list_ordered_by_sort_order_then_id`·`findMaxSortOrder_*`, Task 4 `create_*_appends_*`, Task 5 `create_without_sort_order_appends_max_plus_10`·`public_list_is_flat_array_ordered`), Finding 3(soft-deleted parent → 400 — Task 5 `create_under_soft_deleted_parent_is_400`, Task 4 `create_with_nonexistent_parent_is_400`), Finding 4(@PositiveOrZero — Task 3 DTO, Task 5 `create_negative_sort_order_is_400`).

**2. 플레이스홀더 스캔** — TBD/TODO/"적절히 처리" 없음. 모든 코드 스텝에 완전한 코드 포함.

**3. 타입 일관성** — `Department.create(String,String,String,Long,Integer)`, `update(동일 5인자)`, `applyPatch(String,String,String,Long,Integer)`, `DepartmentService(DepartmentRepository)`(단일 의존), `list():List<DepartmentCardResponse>`, `validateParent(Long,Long)`, `lockHierarchy(long)`, `findByDeletedAtIsNullOrderBySortOrderAscIdAsc()`, `findMaxSortOrder():Optional<Integer>`, `existsByParentIdAndDeletedAtIsNull(Long)`, `DepartmentRefRow.getId()/getTitle()`, `ContentRef("department",id,title)`, `ErrorCode.{INVALID_INPUT_VALUE,RESOURCE_NOT_FOUND,OPTIMISTIC_LOCK_CONFLICT,DEPARTMENT_HAS_CHILDREN}` — 태스크 전반 일치. DTO 필드명(`name`,`description`,`leader`,`parentId`,`sortOrder`,`version`)이 엔티티·서비스·테스트·JSON 본문에서 동일. CardResponse(description 제외)/DetailResponse(description·version 포함, author 없음)가 서비스 매핑·E2E 검증(`$[0].description` doesNotExist / `$.author` doesNotExist)과 일치. 목록 평배열(`$.length()`·`$[n]`·`$.page` doesNotExist)이 컨트롤러 `List` 반환과 일치.
