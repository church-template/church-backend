# 직분(Position) 도메인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 스펙 §5.3 직분 CRUD를 첫 비즈니스 도메인으로 구현한다 — 공개 목록 조회 + 관리자용 생성/수정/삭제(`POSITION_MANAGE`), 코드 수정 없이 데이터로 직분을 관리한다.

**Architecture:** `domain/position` 플랫 패키지(엔티티·리포지토리·서비스·컨트롤러 + `dto/`). `Position`은 `created_at`만 갖는 마스터 데이터라 `BaseTimeEntity`를 상속(soft delete·낙관락·작성자 없음, **물리 삭제**). `members.position_id` FK(`ON DELETE SET NULL`)는 members가 직분보다 나중(로드맵 #8)이라 이번 마이그레이션엔 두지 않는다. 목록은 비페이징 평배열, `sortOrder`는 선택 입력(누락 시 `max+10`, 간격 번호로 중간 삽입 여지). 중복 `name`은 `existsByName` 선검사 + `saveAndFlush` try/catch 백스톱으로 409.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Data JPA, PostgreSQL 16(Flyway V1), Spring Security(`@PreAuthorize` 메서드 보안), JUnit 5 + Mockito + AssertJ + Testcontainers + MockMvc.

**설계 출처:** [`docs/superpowers/specs/2026-06-04-g5-position-domain-design.md`](../specs/2026-06-04-g5-position-domain-design.md) · 스펙 §5.3/§3.2/§6/§7.

**선행 조건:** 브랜치 `20260603_#6_직분_Position_도메인` 체크아웃됨. Testcontainers 테스트는 **Docker 데몬 실행 필요**. 빌드 전 `./gradlew spotlessApply`(palantir 포맷). 커밋 메시지는 한글 + 저장소 스타일 `feat : <설명> #6`. **Co-Authored-By 금지.**

---

## File Structure

생성할 파일(각 1책임):

| 파일 | 책임 |
|---|---|
| `src/main/resources/db/migration/V1__create_positions.sql` | 첫 Flyway 마이그레이션 — positions 테이블·UNIQUE·인덱스 |
| `src/main/java/com/elipair/church/domain/position/Position.java` | 엔티티(`BaseTimeEntity` 상속), `of`/`update` 도메인 메서드 |
| `src/main/java/com/elipair/church/domain/position/PositionRepository.java` | 쿼리 메서드 3종 |
| `src/main/java/com/elipair/church/domain/position/dto/PositionResponse.java` | 응답 record + `from(entity)` |
| `src/main/java/com/elipair/church/domain/position/dto/PositionCreateRequest.java` | 생성 요청(`name @NotBlank`) |
| `src/main/java/com/elipair/church/domain/position/dto/PositionUpdateRequest.java` | 수정 요청(`name` nullable) |
| `src/main/java/com/elipair/church/domain/position/PositionService.java` | list/create/update/delete + normalizeName + saveAndFlush 백스톱 |
| `src/main/java/com/elipair/church/domain/position/PositionController.java` | GET 공개 + admin 3종(`@PreAuthorize`) |
| `src/test/java/com/elipair/church/domain/position/PositionRepositoryTest.java` | `@DataJpaTest` 쿼리·UNIQUE |
| `src/test/java/com/elipair/church/domain/position/PositionServiceTest.java` | Mockito 단위(전 분기) |
| `src/test/java/com/elipair/church/domain/position/PositionApiTest.java` | `@SpringBootTest`+MockMvc 풀스택(인가·검증·CRUD·Flyway validate) |

---

## Task 1: 엔티티 · V1 마이그레이션 · 리포지토리

**Files:**
- Create: `src/main/java/com/elipair/church/domain/position/Position.java`
- Create: `src/main/resources/db/migration/V1__create_positions.sql`
- Create: `src/main/java/com/elipair/church/domain/position/PositionRepository.java`
- Test: `src/test/java/com/elipair/church/domain/position/PositionRepositoryTest.java`

- [ ] **Step 1: 실패하는 리포지토리 테스트 작성**

`@DataJpaTest`는 이 저장소에서 **flyway off + create-drop**(BaseEntityAuditingTest 검증된 패턴)으로만 쓴다. `JpaConfig` import로 `@EnableJpaAuditing`을 켜야 `created_at`(NOT NULL)이 채워진다. `Position`은 앱 스캔(`com.elipair.church`) 안의 실엔티티라 `@EntityScan` override는 불필요.

```java
package com.elipair.church.domain.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class PositionRepositoryTest {

    @Autowired
    private PositionRepository repository;

    @Test
    void findAll_orders_by_sort_order_asc() {
        repository.save(Position.of("장로", 20));
        repository.save(Position.of("목사", 10));

        List<Position> result = repository.findAllByOrderBySortOrderAsc();

        assertThat(result).extracting(Position::getName).containsExactly("목사", "장로");
    }

    @Test
    void existsByName_true_and_false() {
        repository.save(Position.of("권사", 10));

        assertThat(repository.existsByName("권사")).isTrue();
        assertThat(repository.existsByName("없는직분")).isFalse();
    }

    @Test
    void findMaxSortOrder_returns_max_or_empty() {
        assertThat(repository.findMaxSortOrder()).isEqualTo(Optional.empty());

        repository.save(Position.of("목사", 10));
        repository.save(Position.of("장로", 30));

        assertThat(repository.findMaxSortOrder()).contains(30);
    }

    @Test
    void duplicate_name_violates_unique_constraint() {
        repository.saveAndFlush(Position.of("목사", 10));

        assertThatThrownBy(() -> repository.saveAndFlush(Position.of("목사", 20)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditing_populates_created_at() {
        Position saved = repository.saveAndFlush(Position.of("집사", 10));
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.position.PositionRepositoryTest'`
Expected: 컴파일 실패 — `Position`·`PositionRepository` 심볼 없음.

- [ ] **Step 3: Position 엔티티 작성**

`BaseTimeEntity`(createdAt만) 상속. `@Setter` 없이 정적 팩토리 + 부분수정 메서드. JPA용 `protected` 기본생성자.

```java
package com.elipair.church.domain.position;

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
 * 직분(목사·장로·권사…). 권한과 독립된 회원의 선택 속성(스펙 §3.2/§5.3).
 * soft delete·낙관락·작성자 없이 created_at만 갖는 마스터 데이터라 BaseTimeEntity를 상속한다.
 */
@Entity
@Table(name = "positions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    private Position(String name, Integer sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public static Position of(String name, Integer sortOrder) {
        return new Position(name, sortOrder);
    }

    /** 각 인자 null은 미변경(부분 수정). */
    public void update(String name, Integer sortOrder) {
        if (name != null) {
            this.name = name;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }
}
```

- [ ] **Step 4: V1 마이그레이션 작성**

엔티티와 정확히 일치(앱 기동 시 `ddl-auto: validate` 통과). `db/migration` 디렉터리는 이번이 첫 마이그레이션이라 새로 생긴다.

```sql
CREATE TABLE positions (
    id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    sort_order INTEGER     NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_positions_name UNIQUE (name)
);

-- 직분 목록 정렬용(스펙 §6). positions엔 deleted_at이 없어 부분 인덱스가 아니다.
CREATE INDEX idx_positions_sort_order ON positions (sort_order);

-- NOTE(#8 members): members.position_id BIGINT FK -> positions(id) ON DELETE SET NULL은
--   members 테이블 생성 마이그레이션에서 추가한다(직분이 members보다 먼저 구현되므로 여기선 보류).
```

- [ ] **Step 5: PositionRepository 작성**

```java
package com.elipair.church.domain.position;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PositionRepository extends JpaRepository<Position, Long> {

    List<Position> findAllByOrderBySortOrderAsc();

    boolean existsByName(String name);

    /** 빈 테이블이면 Optional.empty()(스칼라 null → empty). */
    @Query("select max(p.sortOrder) from Position p")
    Optional<Integer> findMaxSortOrder();
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.position.PositionRepositoryTest'`
Expected: 5개 테스트 PASS. (Docker 필요)

- [ ] **Step 7: 포맷 후 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/domain/position/Position.java \
        src/main/resources/db/migration/V1__create_positions.sql \
        src/main/java/com/elipair/church/domain/position/PositionRepository.java \
        src/test/java/com/elipair/church/domain/position/PositionRepositoryTest.java
git commit -m "feat : 직분 Position 엔티티·V1 마이그레이션·리포지토리 #6"
```

---

## Task 2: DTO · 서비스

**Files:**
- Create: `src/main/java/com/elipair/church/domain/position/dto/PositionResponse.java`
- Create: `src/main/java/com/elipair/church/domain/position/dto/PositionCreateRequest.java`
- Create: `src/main/java/com/elipair/church/domain/position/dto/PositionUpdateRequest.java`
- Create: `src/main/java/com/elipair/church/domain/position/PositionService.java`
- Test: `src/test/java/com/elipair/church/domain/position/PositionServiceTest.java`

- [ ] **Step 1: DTO 3종 작성**(순수 record, 별도 테스트 없음 — 동작은 서비스/ API 테스트가 검증)

`PositionResponse.java`:

```java
package com.elipair.church.domain.position.dto;

import com.elipair.church.domain.position.Position;
import java.time.LocalDateTime;

public record PositionResponse(Long id, String name, Integer sortOrder, LocalDateTime createdAt) {

    public static PositionResponse from(Position position) {
        return new PositionResponse(
                position.getId(), position.getName(), position.getSortOrder(), position.getCreatedAt());
    }
}
```

`PositionCreateRequest.java`(name 필수):

```java
package com.elipair.church.domain.position.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PositionCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @PositiveOrZero Integer sortOrder) {}
```

`PositionUpdateRequest.java`(name nullable=미변경, `@NotBlank` 금지 — sortOrder만 수정하는 PATCH가 깨지면 안 됨):

```java
package com.elipair.church.domain.position.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PositionUpdateRequest(
        @Size(max = 50) String name,
        @PositiveOrZero Integer sortOrder) {}
```

- [ ] **Step 2: 실패하는 서비스 단위 테스트 작성**

Mockito 단위(Spring 불필요). `isInstanceOfSatisfying`으로 `BusinessException`의 `errorCode`를 검증. `@ExtendWith(MockitoExtension.class)`는 strict stubbing이라 — 각 테스트는 실제 호출되는 스텁만 둔다.

```java
package com.elipair.church.domain.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.position.dto.PositionCreateRequest;
import com.elipair.church.domain.position.dto.PositionResponse;
import com.elipair.church.domain.position.dto.PositionUpdateRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock
    private PositionRepository repository;

    @InjectMocks
    private PositionService service;

    @Test
    void list_maps_repository_result() {
        when(repository.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(Position.of("목사", 10), Position.of("장로", 20)));

        List<PositionResponse> result = service.list();

        assertThat(result).extracting(PositionResponse::name).containsExactly("목사", "장로");
    }

    @Test
    void create_with_explicit_sort_order() {
        when(repository.existsByName("목사")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new PositionCreateRequest("목사", 5));

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(5);
        verify(repository, never()).findMaxSortOrder();
    }

    @Test
    void create_without_sort_order_uses_max_plus_gap() {
        when(repository.existsByName("장로")).thenReturn(false);
        when(repository.findMaxSortOrder()).thenReturn(Optional.of(10));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new PositionCreateRequest("장로", null));

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(20);
    }

    @Test
    void create_without_sort_order_on_empty_table_uses_gap() {
        when(repository.existsByName("목사")).thenReturn(false);
        when(repository.findMaxSortOrder()).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new PositionCreateRequest("목사", null));

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(10);
    }

    @Test
    void create_trims_name() {
        when(repository.existsByName("목사")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(new PositionCreateRequest("  목사  ", 10));

        ArgumentCaptor<Position> captor = ArgumentCaptor.forClass(Position.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("목사");
    }

    @Test
    void create_duplicate_name_precheck_throws() {
        when(repository.existsByName("목사")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new PositionCreateRequest("목사", 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_unique_race_translates_to_duplicate() {
        when(repository.existsByName("목사")).thenReturn(false);
        when(repository.findMaxSortOrder()).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_positions_name"));

        assertThatThrownBy(() -> service.create(new PositionCreateRequest("목사", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
    }

    @Test
    void create_blank_name_throws_invalid_input() {
        assertThatThrownBy(() -> service.create(new PositionCreateRequest("   ", 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(repository, never()).existsByName(any());
    }

    @Test
    void update_name_only_keeps_sort_order() {
        when(repository.findById(1L)).thenReturn(Optional.of(Position.of("목사", 10)));
        when(repository.existsByName("부목사")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        PositionResponse result = service.update(1L, new PositionUpdateRequest("부목사", null));

        assertThat(result.name()).isEqualTo("부목사");
        assertThat(result.sortOrder()).isEqualTo(10);
    }

    @Test
    void update_sort_order_only_keeps_name_and_skips_dup_check() {
        when(repository.findById(1L)).thenReturn(Optional.of(Position.of("목사", 10)));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        PositionResponse result = service.update(1L, new PositionUpdateRequest(null, 99));

        assertThat(result.name()).isEqualTo("목사");
        assertThat(result.sortOrder()).isEqualTo(99);
        verify(repository, never()).existsByName(any());
    }

    @Test
    void update_blank_name_throws_invalid_input() {
        when(repository.findById(1L)).thenReturn(Optional.of(Position.of("목사", 10)));

        assertThatThrownBy(() -> service.update(1L, new PositionUpdateRequest("   ", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    void update_unknown_id_throws_not_found() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, new PositionUpdateRequest("목사", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void update_rename_to_existing_name_throws_duplicate() {
        when(repository.findById(1L)).thenReturn(Optional.of(Position.of("목사", 10)));
        when(repository.existsByName("장로")).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new PositionUpdateRequest("장로", null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void delete_existing_calls_delete_by_id() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void delete_unknown_id_throws_not_found() {
        when(repository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        verify(repository, never()).deleteById(any());
    }
}
```

- [ ] **Step 3: 컴파일 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.position.PositionServiceTest'`
Expected: 컴파일 실패 — `PositionService` 심볼 없음.

- [ ] **Step 4: PositionService 구현**

```java
package com.elipair.church.domain.position;

import com.elipair.church.domain.position.dto.PositionCreateRequest;
import com.elipair.church.domain.position.dto.PositionResponse;
import com.elipair.church.domain.position.dto.PositionUpdateRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PositionService {

    private static final int SORT_ORDER_GAP = 10;

    private final PositionRepository repository;

    public PositionService(PositionRepository repository) {
        this.repository = repository;
    }

    public List<PositionResponse> list() {
        return repository.findAllByOrderBySortOrderAsc().stream()
                .map(PositionResponse::from)
                .toList();
    }

    @Transactional
    public PositionResponse create(PositionCreateRequest request) {
        String name = normalizeName(request.name());
        if (repository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : repository.findMaxSortOrder().map(max -> max + SORT_ORDER_GAP).orElse(SORT_ORDER_GAP);
        return persist(Position.of(name, sortOrder));
    }

    @Transactional
    public PositionResponse update(Long id, PositionUpdateRequest request) {
        Position position =
                repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        String name = null;
        if (request.name() != null) {
            name = normalizeName(request.name());
            if (!name.equals(position.getName()) && repository.existsByName(name)) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
            }
        }
        position.update(name, request.sortOrder());
        return persist(position);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        repository.deleteById(id);
    }

    // name UNIQUE 경합 백스톱: 선검사를 빠져나간 동시 생성/수정을 saveAndFlush로 즉시 flush해 잡는다.
    private PositionResponse persist(Position position) {
        try {
            return PositionResponse.from(repository.saveAndFlush(position));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    private String normalizeName(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "직분 이름은 공백일 수 없습니다");
        }
        return trimmed;
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.position.PositionServiceTest'`
Expected: 15개 테스트 PASS.

- [ ] **Step 6: 포맷 후 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/domain/position/dto/ \
        src/main/java/com/elipair/church/domain/position/PositionService.java \
        src/test/java/com/elipair/church/domain/position/PositionServiceTest.java
git commit -m "feat : 직분 DTO·서비스(생성/수정/삭제·중복 백스톱) #6"
```

---

## Task 3: 컨트롤러 · 풀스택 API 테스트

**Files:**
- Create: `src/main/java/com/elipair/church/domain/position/PositionController.java`
- Test: `src/test/java/com/elipair/church/domain/position/PositionApiTest.java`

> 설계 문서의 "PositionControllerTest(@WebMvcTest)"와 "PositionIntegrationTest"를 **하나의 `@SpringBootTest`+MockMvc 풀스택 테스트로 통합**한다 — 인가(JWT 필터+메서드보안)는 전체 보안 컨텍스트가 필요하고, 저장소에 `SecurityConfigPathRulesTest`라는 검증된 선례가 있다. 이 테스트가 실 Flyway V1 + `ddl-auto: validate`도 함께 검증한다(마이그레이션↔엔티티 정합).

- [ ] **Step 1: 실패하는 API 테스트 작성**

`@SpringBootTest`+`@AutoConfigureMockMvc`+`@Import(TestcontainersConfiguration.class)`. `JwtTokenProvider`로 권한 토큰 발급(`issueAccess(MemberPrincipal, position, permissions)`). MockMvc 요청 본문에 한글이 있으므로 `characterEncoding(UTF_8)`, 응답은 `getContentAsString(UTF_8)`. 정렬 검증은 인코딩에 안전한 `sortOrder`(숫자)로 한다. 각 테스트 격리는 `@AfterEach repository.deleteAll()`.

```java
package com.elipair.church.domain.position;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
class PositionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private PositionRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    private String admin() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(1L, "uuid-admin", "관리자", 1000), null, List.of("POSITION_MANAGE"));
    }

    private String otherPermission() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(2L, "uuid-user", "사용자", 100), null, List.of("SERMON_WRITE"));
    }

    private String body(String name, Integer sortOrder) {
        return sortOrder == null
                ? "{\"name\":\"" + name + "\"}"
                : "{\"name\":\"" + name + "\",\"sortOrder\":" + sortOrder + "}";
    }

    /** 관리자로 직분 생성 후 생성된 id 반환. */
    private long createPosition(String name, int sortOrder) throws Exception {
        String json = mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, sortOrder)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void public_list_is_open_and_sorted_as_plain_array() throws Exception {
        createPosition("목사", 20);
        createPosition("장로", 10);

        mockMvc.perform(get("/api/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sortOrder").value(10))
                .andExpect(jsonPath("$[1].sortOrder").value(20))
                .andExpect(jsonPath("$.page").doesNotExist()); // 페이지 봉투가 아닌 평배열
    }

    @Test
    void create_without_sort_order_appends_with_gap() throws Exception {
        mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("목사", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("목사"))
                .andExpect(jsonPath("$.sortOrder").value(10)); // 빈 테이블 → 10

        mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("장로", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(20)); // max+10
    }

    @Test
    void create_anonymous_is_401() throws Exception {
        mockMvc.perform(post("/api/admin/positions")
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("목사", 10)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void create_without_permission_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", otherPermission())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("목사", 10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_blank_name_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ", 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void create_duplicate_name_is_409() throws Exception {
        createPosition("목사", 10);

        mockMvc.perform(post("/api/admin/positions")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("목사", 20)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void patch_updates_sort_order_only() throws Exception {
        long id = createPosition("목사", 10);

        mockMvc.perform(patch("/api/admin/positions/" + id)
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("목사"))
                .andExpect(jsonPath("$.sortOrder").value(99));
    }

    @Test
    void patch_unknown_id_is_404() throws Exception {
        mockMvc.perform(patch("/api/admin/positions/999999")
                        .header("Authorization", admin())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("목사", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void delete_returns_204_and_removes() throws Exception {
        long id = createPosition("목사", 10);

        mockMvc.perform(delete("/api/admin/positions/" + id).header("Authorization", admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/positions")).andExpect(jsonPath("$.length()").value(0));
    }
}
```

- [ ] **Step 2: 컴파일/실행 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.position.PositionApiTest'`
Expected: 컴파일 실패 — `PositionController` 없음(엔드포인트 미존재).

- [ ] **Step 3: PositionController 구현**

```java
package com.elipair.church.domain.position;

import com.elipair.church.domain.position.dto.PositionCreateRequest;
import com.elipair.church.domain.position.dto.PositionResponse;
import com.elipair.church.domain.position.dto.PositionUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 직분 API(스펙 §5.3). GET은 공개(SecurityConfig anyRequest permitAll),
 * admin 3종은 /api/admin/** 인증 + POSITION_MANAGE 메서드 보안.
 */
@RestController
public class PositionController {

    private final PositionService service;

    public PositionController(PositionService service) {
        this.service = service;
    }

    @GetMapping("/api/positions")
    public List<PositionResponse> list() {
        return service.list();
    }

    @PostMapping("/api/admin/positions")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    public ResponseEntity<PositionResponse> create(@Valid @RequestBody PositionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/api/admin/positions/{id}")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    public PositionResponse update(@PathVariable Long id, @Valid @RequestBody PositionUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/api/admin/positions/{id}")
    @PreAuthorize("hasAuthority('POSITION_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.position.PositionApiTest'`
Expected: 9개 테스트 PASS. (실 Flyway V1 적용 + `ddl-auto: validate` 통과로 마이그레이션↔엔티티 정합도 함께 검증됨. Docker 필요)

- [ ] **Step 5: 포맷 후 커밋**

```bash
./gradlew spotlessApply
git add src/main/java/com/elipair/church/domain/position/PositionController.java \
        src/test/java/com/elipair/church/domain/position/PositionApiTest.java
git commit -m "feat : 직분 컨트롤러·풀스택 API 테스트(인가·검증·CRUD) #6"
```

---

## Task 4: 전체 빌드 · 커버리지 검증

**Files:** 없음(검증만).

- [ ] **Step 1: 전체 클린 빌드**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL. 전체 테스트 green, **ArchUnit `ArchitectureTest` 통과**(positions는 `global`만 의존 → `domain → global` 단방향 유지). `ddl-auto: validate`로 부팅하는 모든 `@SpringBootTest`가 V1 마이그레이션과 정합. (Docker 필요)

- [ ] **Step 2: 신규 코드 커버리지 확인**

Run: `open build/reports/jacoco/test/html/index.html` (또는 `build/reports/jacoco/test/jacocoTestReport.xml` 확인)
Expected: `domain.position` 패키지 라인 커버리지 80%+ (서비스 분기·컨트롤러 경로 전부 테스트로 커버).

- [ ] **Step 3: (필요 시) 부족분 보강**

커버리지 80% 미만이면 누락 분기에 단위 테스트를 추가하고 Task 2/3 커밋에 amend하지 말고 별도 커밋:

```bash
git add src/test/java/com/elipair/church/domain/position/
git commit -m "test : 직분 커버리지 보강 #6"
```

---

## Self-Review

**1. Spec coverage** (설계 문서 대비):
- 엔티티(BaseTimeEntity·name UNIQUE·sortOrder) → Task 1 ✅
- V1 마이그레이션(+ members FK 주석 보류) → Task 1 ✅
- 리포지토리 3메서드 → Task 1 ✅
- 비페이징 평배열 GET → Task 3 (`$.page doesNotExist`, `$.isArray`) ✅
- admin 3종 + `POSITION_MANAGE` 메서드 보안 → Task 3 (401/403/200) ✅
- sortOrder 선택·max+10·빈 테이블 10 → Task 2·3 ✅
- Create/Update name 검증 분리(+normalizeName blank 거부) → Task 2 (단위) + Task 3 (400) ✅
- 중복 409(existsByName + saveAndFlush 백스톱) → Task 2 (선검사·race 단위) + Task 3 (409) ✅
- 없는 id 404 → Task 2·3 ✅
- 물리 삭제 204 → Task 3 ✅
- 신규 ErrorCode 0, GlobalExceptionHandler 미수정 ✅ (서비스 내부 변환)
- 테스트 3종(repo 슬라이스·service 단위·풀스택 API) → Task 1·2·3 ✅

**2. Placeholder scan:** TBD/TODO/"적절히 처리" 없음 — 전 스텝에 실제 코드·명령·기대 출력 포함. ✅

**3. Type consistency:**
- `Position.of(String, Integer)` / `update(String, Integer)` — Task 1 정의, Task 2 서비스에서 동일 시그니처 사용 ✅
- `PositionResponse.from(Position)` / 접근자 `name()`·`sortOrder()`·`id()` — Task 2 정의, Task 2 테스트·Task 3 JSON 필드명(`name`·`sortOrder`·`id`)과 일치 ✅
- `repository.findMaxSortOrder() : Optional<Integer>`, `existsByName`, `findAllByOrderBySortOrderAsc`, `saveAndFlush`, `existsById`, `deleteById` — Task 1 정의 ↔ Task 2 사용 일치 ✅
- `provider.issueAccess(MemberPrincipal, String, List<String>)` / `MemberPrincipal(Long, String, String, int)` — 실제 시그니처와 일치(SecurityConfigPathRulesTest 확인) ✅
- `BusinessException(ErrorCode)` / `BusinessException(ErrorCode, String)` + `getErrorCode()` — 실제 클래스와 일치 ✅
- ErrorCode 키(`DUPLICATE_RESOURCE`·`RESOURCE_NOT_FOUND`·`INVALID_INPUT_VALUE`·`INVALID_TOKEN`·`ACCESS_DENIED`) — 실제 enum과 일치 ✅

검토 완료, 인라인 수정 불요.
