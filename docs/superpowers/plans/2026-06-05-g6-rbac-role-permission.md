# 역할·권한(RBAC) 도메인 + 시드 구현 계획 (G6 / #7)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Discord식 동적 RBAC의 역할·권한 코어(roles·permissions·role_permissions 테이블 + 시드 + 역할 CRUD/권한설정/권한목록 API)를 구현하고, 위계 규칙을 같은 레벨 허용(`<=`)으로 전환한다.

**Architecture:** 패키지-by-feature(`domain/role`, 플랫). 인가는 permission 단위(`@PreAuthorize("hasAuthority('ROLE_MANAGE')")`), 위계는 기존 `global/security/RoleHierarchyValidator` 재사용. 권한 카탈로그·역할 시드는 Flyway `V2`로 고정. `member_roles`·SUPER_ADMIN 계정·회원-역할 부여/회수는 #8(회원)로 이연.

**Tech Stack:** Spring Boot 4.0.6, Java 21, JPA/Hibernate, Flyway, PostgreSQL(Testcontainers), JUnit5 + Mockito + AssertJ + MockMvc.

**설계 문서:** `docs/superpowers/specs/2026-06-05-g6-rbac-role-permission-design.md` (커밋 `ba3e7ba`).

**현재 브랜치:** `20260603_#7_역할_권한_RBAC_도메인_시드` (이미 격리됨, 워크트리 불필요).

---

## 파일 구조

| 파일 | 책임 | 신규/수정 |
|---|---|---|
| `src/main/java/.../global/security/RoleHierarchyValidator.java` | 위계 규칙 `<=` 전환 | 수정 |
| `src/test/java/.../global/security/RoleHierarchyValidatorTest.java` | 위 규칙 테스트 | 수정 |
| `docs/church-backend-spec.md` §4.3 / `.claude/rules/rbac-authorization.md` / `CLAUDE.md` | 위계 문구 동기화 | 수정 |
| `src/main/resources/db/migration/V2__create_rbac.sql` | RBAC 테이블 + 시드 | 신규 |
| `src/main/java/.../domain/role/Permission.java` | 권한 엔티티(plain) | 신규 |
| `src/main/java/.../domain/role/Role.java` | 역할 엔티티(BaseTimeEntity, @ManyToMany 권한) | 신규 |
| `src/main/java/.../domain/role/PermissionRepository.java` | 권한 조회(name 정렬·in) | 신규 |
| `src/main/java/.../domain/role/RoleRepository.java` | 역할 조회(priority 정렬·@EntityGraph) | 신규 |
| `src/main/java/.../domain/role/dto/PermissionResponse.java` | 권한 응답 | 신규 |
| `src/main/java/.../domain/role/dto/RoleResponse.java` | 역할 응답(permissions 포함) | 신규 |
| `src/main/java/.../domain/role/dto/RoleCreateRequest.java` | 역할 생성 요청 | 신규 |
| `src/main/java/.../domain/role/dto/RoleUpdateRequest.java` | 역할 수정 요청(부분) | 신규 |
| `src/main/java/.../domain/role/dto/RolePermissionsRequest.java` | 권한 일괄 설정 요청 | 신규 |
| `src/main/java/.../domain/role/PermissionService.java` | 권한 목록 | 신규 |
| `src/main/java/.../domain/role/RoleService.java` | 역할 CRUD + 권한설정 + 위계 검증 | 신규 |
| `src/main/java/.../domain/role/PermissionController.java` | `GET /api/admin/permissions` | 신규 |
| `src/main/java/.../domain/role/RoleController.java` | 역할 5종 엔드포인트 | 신규 |
| `src/test/java/.../domain/role/RoleRepositoryTest.java` | repo 단위 | 신규 |
| `src/test/java/.../domain/role/RbacSeedIntegrityTest.java` | 시드 무결성 | 신규 |
| `src/test/java/.../domain/role/RoleServiceTest.java` | 서비스 단위 | 신규 |
| `src/test/java/.../domain/role/RoleApiTest.java` | 역할 API 통합 | 신규 |
| `src/test/java/.../domain/role/PermissionApiTest.java` | 권한 목록 API 통합 | 신규 |

패키지 prefix: `com.elipair.church`. 의존 방향 `domain → global` 단방향(ArchUnit `ArchitectureTest`가 강제).

---

## Task 1: 위계 규칙 `<=`(같은 레벨 허용)으로 전환

**Files:**
- Modify: `src/test/java/com/elipair/church/global/security/RoleHierarchyValidatorTest.java`
- Modify: `src/main/java/com/elipair/church/global/security/RoleHierarchyValidator.java:14-19`
- Modify: `docs/church-backend-spec.md:187-188`
- Modify: `.claude/rules/rbac-authorization.md:19`
- Modify: `CLAUDE.md:57`

- [ ] **Step 1: 테스트를 `<=` 규칙으로 수정 (실패 유도)**

`RoleHierarchyValidatorTest.java`에서 `assignable_rejects_equal_or_higher_priority` 메서드(현재 15-21행)를 아래로 교체하고, `mutable_allows_non_system_lower_priority` 메서드 아래에 같은 레벨 허용 케이스를 추가한다.

교체(기존 `assignable_rejects_equal_or_higher_priority` 전체 → 아래):
```java
    @Test
    void assignable_allows_equal_rejects_higher_priority() {
        // 같은 레벨은 허용(<=)
        assertThatCode(() -> validator.validateAssignable(900, 900)).doesNotThrowAnyException();
        // 초과는 거부
        assertThatThrownBy(() -> validator.validateAssignable(900, 1000))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }
```

추가(`mutable_allows_non_system_lower_priority` 메서드 바로 뒤):
```java
    @Test
    void mutable_allows_equal_priority_non_system() {
        assertThatCode(() -> validator.validateMutable(900, 900, false)).doesNotThrowAnyException();
    }
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.RoleHierarchyValidatorTest'`
Expected: FAIL — `assignable_allows_equal_rejects_higher_priority`가 `validateAssignable(900,900)`에서 예외 발생(현재 구현은 `>=` 거부), `mutable_allows_equal_priority_non_system`도 실패.

- [ ] **Step 3: 검증기 구현을 `>` 로 변경**

`RoleHierarchyValidator.java`의 `validateAssignable`(14-19행)를 아래로 교체:
```java
    /** 대상 역할 priority가 요청자 maxPriority 이하여야 한다(같은 레벨 허용, 초과만 escalation 차단). */
    public void validateAssignable(int requesterMaxPriority, int targetPriority) {
        if (targetPriority > requesterMaxPriority) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "대상 역할의 priority가 요청자 권한을 초과합니다");
        }
    }
```
(`validateMutable`은 내부에서 `validateAssignable`를 호출하므로 자동으로 `<=` 규칙을 따른다 — 수정 불필요.)

- [ ] **Step 4: 테스트 실행해 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.global.security.RoleHierarchyValidatorTest'`
Expected: PASS (전체 그린)

- [ ] **Step 5: 스펙·규칙 문구 동기화**

`docs/church-backend-spec.md` 187-188행 교체:
- old(187): `- 회원에게 역할 부여/회수: 대상 역할의 priority가 요청자의 maxPriority보다 **낮아야** 한다. 같거나 높으면 거부(403).`
- new(187): `- 회원에게 역할 부여/회수: 대상 역할의 priority가 요청자의 maxPriority **이하여야** 한다. 초과하면 거부(403).`
- old(188): `- 역할 자체 수정(\`PATCH /roles/{id}\`), 삭제(\`DELETE /roles/{id}\`), 권한 변경(\`PUT /roles/{id}/permissions\`): 대상 역할의 priority가 요청자 maxPriority보다 **낮아야** 한다. 자기와 같거나 높은 역할은 건드릴 수 없다(is_system 여부와 무관하게 추가 검증).`
- new(188): `- 역할 자체 수정(\`PATCH /roles/{id}\`), 삭제(\`DELETE /roles/{id}\`), 권한 변경(\`PUT /roles/{id}/permissions\`): 대상 역할의 priority가 요청자 maxPriority **이하여야** 한다. 자기보다 높은 역할은 건드릴 수 없다(같은 레벨까지 허용, is_system 여부와 무관하게 추가 검증).`

`.claude/rules/rbac-authorization.md` 19행 교체:
- old: `A requester may only act on a role whose \`priority\` is **strictly less than** their own \`maxPriority\`. Equal or higher → \`403\`. Apply this identically to **all** of:`
- new: `A requester may only act on a role whose \`priority\` is **at or below** their own \`maxPriority\` (same level allowed). Strictly higher → \`403\`. Apply this identically to **all** of:`

`CLAUDE.md` 57행에서 문구 교체:
- old 부분: `you may only grant/modify/delete a role whose priority is **strictly below** your own \`maxPriority\` (escalation guard)`
- new 부분: `you may only grant/modify/delete a role whose priority is **at or below** your own \`maxPriority\` (same level allowed; escalation guard blocks strictly-higher only)`

- [ ] **Step 6: 전체 테스트로 회귀 없음 확인 후 커밋**

Run: `./gradlew test`
Expected: PASS (전체 그린)
```bash
git add src/main/java/com/elipair/church/global/security/RoleHierarchyValidator.java \
        src/test/java/com/elipair/church/global/security/RoleHierarchyValidatorTest.java \
        docs/church-backend-spec.md .claude/rules/rbac-authorization.md CLAUDE.md
git commit -m "feat : 역할 위계 검증을 같은 레벨 허용(<=)으로 변경 #7"
```

---

## Task 2: Flyway V2 마이그레이션 + 시드

**Files:**
- Create: `src/main/resources/db/migration/V2__create_rbac.sql`

- [ ] **Step 1: 마이그레이션 작성**

`V2__create_rbac.sql` 전체:
```sql
-- 권한(고정 카탈로그). 타임스탬프 없음(스펙 §3.2).
CREATE TABLE permissions (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uq_permissions_name UNIQUE (name)
);

-- 역할(관리자가 동적 생성). created_at만, soft-delete/version 없음 → 물리 삭제.
CREATE TABLE roles (
    id          BIGINT  GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    priority    INTEGER     NOT NULL,
    is_system   BOOLEAN     NOT NULL DEFAULT FALSE,
    description VARCHAR(255),
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT uq_roles_name UNIQUE (name)
);
CREATE INDEX idx_roles_priority ON roles (priority);

-- 역할 ↔ 권한 (다대다). 역할 물리 삭제 시 링크 동반 삭제(role 측 CASCADE). permission 측은 RESTRICT — 고정 카탈로그 보호.
-- NOTE(#8 members): member_roles(member_id, role_id)와 members FK, 최초 SUPER_ADMIN 계정 시드는
--   members 마이그레이션에서 추가한다. member_roles.role_id -> roles(id)는 RESTRICT(앱 레벨 블로킹 삭제).
CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles (id)       ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE RESTRICT
);

-- 권한 12종(스펙 §3.3)
INSERT INTO permissions (name, description) VALUES
    ('SERMON_WRITE',    '설교 등록/수정/삭제'),
    ('NOTICE_WRITE',    '공지 등록/수정/삭제'),
    ('EVENT_WRITE',     '일정 등록/수정/삭제'),
    ('DEPT_WRITE',      '교구/부서 등록/수정/삭제'),
    ('MEMBER_MANAGE',   '회원 조회/관리'),
    ('ROLE_MANAGE',     '역할·권한 관리'),
    ('POSITION_MANAGE', '직분 관리'),
    ('MEDIA_MANAGE',    '미디어 업로드/조회/삭제'),
    ('TAG_MANAGE',      '태그 추가/수정/삭제'),
    ('GALLERY_WRITE',   '갤러리 업로드/수정/삭제'),
    ('GALLERY_VIEW',    '갤러리 조회(회원 전용 열람)'),
    ('BULLETIN_WRITE',  '주보 업로드/수정/삭제');

-- 역할 4종(스펙 §3.3)
INSERT INTO roles (name, priority, is_system, description, created_at) VALUES
    ('SUPER_ADMIN', 1000, TRUE,  '최고 관리자',  now()),
    ('ADMIN',        900, TRUE,  '관리자',       now()),
    ('MEMBER',       100, FALSE, '교인',         now()),
    ('USER',           0, TRUE,  '일반 사용자',  now());

-- SUPER_ADMIN·ADMIN = 전 12권한
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN');

-- MEMBER = GALLERY_VIEW
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name = 'GALLERY_VIEW'
WHERE r.name = 'MEMBER';
-- USER = 권한 없음
```

- [ ] **Step 2: 마이그레이션이 깨끗이 적용되는지 확인**

Run: `./gradlew test --tests 'com.elipair.church.ChurchBackendApplicationTests'`
Expected: PASS — 컨텍스트 로드 시 Testcontainer에 V1·V2 Flyway가 순서대로 적용됨(엔티티는 아직 없으므로 validate는 기존 테이블만 검사, 신규 테이블은 무해).

- [ ] **Step 3: 커밋**
```bash
git add src/main/resources/db/migration/V2__create_rbac.sql
git commit -m "feat : RBAC 테이블·시드 마이그레이션(V2) 추가 #7"
```

---

## Task 3: Permission·Role 엔티티 + 리포지토리 + 시드 무결성 테스트

**Files:**
- Create: `src/main/java/com/elipair/church/domain/role/Permission.java`
- Create: `src/main/java/com/elipair/church/domain/role/Role.java`
- Create: `src/main/java/com/elipair/church/domain/role/PermissionRepository.java`
- Create: `src/main/java/com/elipair/church/domain/role/RoleRepository.java`
- Test: `src/test/java/com/elipair/church/domain/role/RoleRepositoryTest.java`
- Test: `src/test/java/com/elipair/church/domain/role/RbacSeedIntegrityTest.java`

- [ ] **Step 1: 엔티티 작성**

`Permission.java`:
```java
package com.elipair.church.domain.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 권한(고정 카탈로그). 역할과 다대다, 시드로만 생성된다(스펙 §3.2). */
@Entity
@Table(name = "permissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    private Permission(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public static Permission of(String name, String description) {
        return new Permission(name, description);
    }
}
```

`Role.java`:
```java
package com.elipair.church.domain.role;

import com.elipair.church.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 역할(권한 묶음 + priority 위계). created_at만 갖고 soft-delete/version 없는 마스터 데이터라
 * BaseTimeEntity를 상속하고 물리 삭제한다(스펙 §3.2). API 생성 역할은 is_system=false 고정.
 */
@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false)
    private int priority;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    @Column(length = 255)
    private String description;

    // 응답 순서를 name ASC로 고정(Set 반환 순서 불안정 방지). 삭제 시 role_permissions는 DB CASCADE.
    @ManyToMany(fetch = FetchType.LAZY)
    @OrderBy("name ASC")
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new LinkedHashSet<>();

    private Role(String name, int priority, String description) {
        this.name = name;
        this.priority = priority;
        this.isSystem = false;
        this.description = description;
    }

    /** API 생성용 — is_system은 항상 false(시스템 역할은 시드로만 생성). */
    public static Role create(String name, int priority, String description) {
        return new Role(name, priority, description);
    }

    /** 각 인자 null은 미변경(부분 수정). */
    public void update(String name, Integer priority, String description) {
        if (name != null) {
            this.name = name;
        }
        if (priority != null) {
            this.priority = priority;
        }
        if (description != null) {
            this.description = description;
        }
    }

    /** 권한 전체 교체(PUT 시맨틱). */
    public void replacePermissions(Collection<Permission> next) {
        this.permissions.clear();
        this.permissions.addAll(next);
    }
}
```

- [ ] **Step 2: 리포지토리 작성**

`PermissionRepository.java`:
```java
package com.elipair.church.domain.role;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByNameAsc();

    List<Permission> findByNameIn(Collection<String> names);
}
```

`RoleRepository.java`:
```java
package com.elipair.church.domain.role;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    /** priority 내림차순 + permissions 즉시 로딩(목록 N+1 회피). */
    @EntityGraph(attributePaths = "permissions")
    List<Role> findAllByOrderByPriorityDesc();

    boolean existsByName(String name);
}
```

- [ ] **Step 3: repo 단위 테스트 작성 (실패 유도)**

`RoleRepositoryTest.java`:
```java
package com.elipair.church.domain.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.config.JpaConfig;
import java.util.List;
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
class RoleRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Test
    void findAll_orders_by_priority_desc_and_fetches_permissions() {
        Permission view = permissionRepository.save(Permission.of("GALLERY_VIEW", "갤러리 조회"));
        Role low = Role.create("LOW", 100, "하위");
        low.replacePermissions(List.of(view));
        roleRepository.save(low);
        roleRepository.save(Role.create("HIGH", 900, "상위"));

        List<Role> result = roleRepository.findAllByOrderByPriorityDesc();

        assertThat(result).extracting(Role::getName).containsExactly("HIGH", "LOW");
        assertThat(result.get(1).getPermissions()).extracting(Permission::getName).containsExactly("GALLERY_VIEW");
    }

    @Test
    void existsByName_true_and_false() {
        roleRepository.save(Role.create("EDITOR", 500, "편집자"));

        assertThat(roleRepository.existsByName("EDITOR")).isTrue();
        assertThat(roleRepository.existsByName("NOPE")).isFalse();
    }

    @Test
    void duplicate_name_violates_unique_constraint() {
        roleRepository.saveAndFlush(Role.create("EDITOR", 500, "편집자"));

        assertThatThrownBy(() -> roleRepository.saveAndFlush(Role.create("EDITOR", 600, "중복")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permission_findByNameIn_returns_matches_only() {
        permissionRepository.save(Permission.of("SERMON_WRITE", "설교"));
        permissionRepository.save(Permission.of("NOTICE_WRITE", "공지"));

        assertThat(permissionRepository.findByNameIn(List.of("SERMON_WRITE", "NOPE")))
                .extracting(Permission::getName)
                .containsExactly("SERMON_WRITE");
    }
}
```

- [ ] **Step 4: repo 테스트 실행해 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.role.RoleRepositoryTest'`
Expected: PASS

- [ ] **Step 5: 시드 무결성 테스트 작성**

`RbacSeedIntegrityTest.java`:
```java
package com.elipair.church.domain.role;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RbacSeedIntegrityTest {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void seeds_twelve_permissions() {
        assertThat(permissionRepository.findAllByOrderByNameAsc())
                .extracting(Permission::getName)
                .containsExactlyInAnyOrder(
                        "SERMON_WRITE", "NOTICE_WRITE", "EVENT_WRITE", "DEPT_WRITE",
                        "MEMBER_MANAGE", "ROLE_MANAGE", "POSITION_MANAGE", "MEDIA_MANAGE",
                        "TAG_MANAGE", "GALLERY_WRITE", "GALLERY_VIEW", "BULLETIN_WRITE");
    }

    @Test
    void seeds_four_roles_with_priority_and_system_flag() {
        Map<String, Role> byName = roleRepository.findAllByOrderByPriorityDesc().stream()
                .collect(Collectors.toMap(Role::getName, Function.identity()));

        assertThat(byName.get("SUPER_ADMIN").getPriority()).isEqualTo(1000);
        assertThat(byName.get("SUPER_ADMIN").isSystem()).isTrue();
        assertThat(byName.get("ADMIN").getPriority()).isEqualTo(900);
        assertThat(byName.get("ADMIN").isSystem()).isTrue();
        assertThat(byName.get("MEMBER").getPriority()).isEqualTo(100);
        assertThat(byName.get("MEMBER").isSystem()).isFalse();
        assertThat(byName.get("USER").getPriority()).isEqualTo(0);
        assertThat(byName.get("USER").isSystem()).isTrue();
    }

    @Test
    void role_permission_matrix_matches_spec() {
        Map<String, Role> byName = roleRepository.findAllByOrderByPriorityDesc().stream()
                .collect(Collectors.toMap(Role::getName, Function.identity()));

        assertThat(byName.get("SUPER_ADMIN").getPermissions()).hasSize(12);
        assertThat(byName.get("ADMIN").getPermissions()).hasSize(12);
        assertThat(byName.get("MEMBER").getPermissions())
                .extracting(Permission::getName)
                .containsExactly("GALLERY_VIEW");
        assertThat(byName.get("USER").getPermissions()).isEmpty();
    }

    @Test
    void permission_findByNameIn_resolves_known_keys() {
        List<Permission> found = permissionRepository.findByNameIn(List.of("SERMON_WRITE", "GALLERY_VIEW", "NOPE"));
        assertThat(found).extracting(Permission::getName).containsExactlyInAnyOrder("SERMON_WRITE", "GALLERY_VIEW");
    }
}
```

- [ ] **Step 6: 시드 테스트 실행해 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.role.RbacSeedIntegrityTest'`
Expected: PASS

- [ ] **Step 7: 전체 테스트 회귀 확인 후 커밋**

Run: `./gradlew test`
Expected: PASS (엔티티가 V2 테이블에 매핑되어 모든 `@SpringBootTest` validate 통과)
```bash
git add src/main/java/com/elipair/church/domain/role/Permission.java \
        src/main/java/com/elipair/church/domain/role/Role.java \
        src/main/java/com/elipair/church/domain/role/PermissionRepository.java \
        src/main/java/com/elipair/church/domain/role/RoleRepository.java \
        src/test/java/com/elipair/church/domain/role/RoleRepositoryTest.java \
        src/test/java/com/elipair/church/domain/role/RbacSeedIntegrityTest.java
git commit -m "feat : Role·Permission 엔티티·리포지토리 추가 #7"
```

---

## Task 4: 전체 권한 목록 API (`GET /api/admin/permissions`)

**Files:**
- Create: `src/main/java/com/elipair/church/domain/role/dto/PermissionResponse.java`
- Create: `src/main/java/com/elipair/church/domain/role/PermissionService.java`
- Create: `src/main/java/com/elipair/church/domain/role/PermissionController.java`
- Test: `src/test/java/com/elipair/church/domain/role/PermissionApiTest.java`

- [ ] **Step 1: PermissionResponse 작성**
```java
package com.elipair.church.domain.role.dto;

import com.elipair.church.domain.role.Permission;

public record PermissionResponse(Long id, String name, String description) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getName(), permission.getDescription());
    }
}
```

- [ ] **Step 2: PermissionService 작성**
```java
package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.PermissionResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository repository;

    public PermissionService(PermissionRepository repository) {
        this.repository = repository;
    }

    public List<PermissionResponse> list() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(PermissionResponse::from)
                .toList();
    }
}
```

- [ ] **Step 3: PermissionController 작성**
```java
package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.PermissionResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 권한 카탈로그 조회(스펙 §5.4). /api/admin/** 인증 + ROLE_MANAGE. */
@RestController
@RequestMapping("/api/admin/permissions")
@PreAuthorize("hasAuthority('ROLE_MANAGE')")
public class PermissionController {

    private final PermissionService service;

    public PermissionController(PermissionService service) {
        this.service = service;
    }

    @GetMapping
    public List<PermissionResponse> list() {
        return service.list();
    }
}
```

- [ ] **Step 4: API 통합 테스트 작성 (실패 유도)**

`PermissionApiTest.java`:
```java
package com.elipair.church.domain.role;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PermissionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    private String roleManager() {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(1L, "uuid-admin", "관리자", 1000), null, List.of("ROLE_MANAGE"));
    }

    private String otherPermission() {
        return "Bearer "
                + provider.issueAccess(new MemberPrincipal(2L, "uuid-user", "사용자", 100), null, List.of("SERMON_WRITE"));
    }

    @Test
    void lists_twelve_permissions_sorted_by_name() throws Exception {
        mockMvc.perform(get("/api/admin/permissions").header("Authorization", roleManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].name").value("BULLETIN_WRITE")); // name ASC 첫 항목
    }

    @Test
    void anonymous_is_401() throws Exception {
        mockMvc.perform(get("/api/admin/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void without_role_manage_is_403() throws Exception {
        mockMvc.perform(get("/api/admin/permissions").header("Authorization", otherPermission()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}
```

- [ ] **Step 5: 테스트 실행해 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.role.PermissionApiTest'`
Expected: PASS (`name ASC` 정렬상 `BULLETIN_WRITE`가 첫 항목 — B가 알파벳 최선두)

- [ ] **Step 6: 커밋**
```bash
git add src/main/java/com/elipair/church/domain/role/dto/PermissionResponse.java \
        src/main/java/com/elipair/church/domain/role/PermissionService.java \
        src/main/java/com/elipair/church/domain/role/PermissionController.java \
        src/test/java/com/elipair/church/domain/role/PermissionApiTest.java
git commit -m "feat : 전체 권한 목록 조회 API 추가 #7"
```

---

## Task 5: 역할 목록 API (`GET /api/admin/roles`)

**Files:**
- Create: `src/main/java/com/elipair/church/domain/role/dto/RoleResponse.java`
- Create: `src/main/java/com/elipair/church/domain/role/RoleService.java`
- Create: `src/main/java/com/elipair/church/domain/role/RoleController.java`
- Test: `src/test/java/com/elipair/church/domain/role/RoleApiTest.java`

- [ ] **Step 1: RoleResponse 작성**
```java
package com.elipair.church.domain.role.dto;

import com.elipair.church.domain.role.Permission;
import com.elipair.church.domain.role.Role;
import java.util.List;

public record RoleResponse(
        Long id, String name, int priority, boolean isSystem, String description, List<PermissionResponse> permissions) {

    public static RoleResponse from(Role role) {
        List<PermissionResponse> permissions = role.getPermissions().stream()
                .map(PermissionResponse::from)
                .toList();
        return new RoleResponse(
                role.getId(), role.getName(), role.getPriority(), role.isSystem(), role.getDescription(), permissions);
    }
}
```

- [ ] **Step 2: RoleService 작성 (list만, 이후 태스크에서 메서드 추가)**
```java
package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.RoleResponse;
import com.elipair.church.global.security.RoleHierarchyValidator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleHierarchyValidator hierarchyValidator;

    public RoleService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RoleHierarchyValidator hierarchyValidator) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.hierarchyValidator = hierarchyValidator;
    }

    public List<RoleResponse> list() {
        return roleRepository.findAllByOrderByPriorityDesc().stream()
                .map(RoleResponse::from)
                .toList();
    }
}
```

- [ ] **Step 3: RoleController 작성 (list만)**
```java
package com.elipair.church.domain.role;

import com.elipair.church.domain.role.dto.RoleResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 역할·권한 관리 API(스펙 §5.4). /api/admin/** 인증 + ROLE_MANAGE(클래스 레벨). */
@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasAuthority('ROLE_MANAGE')")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return service.list();
    }
}
```

- [ ] **Step 4: RoleApiTest 작성 (목록 케이스, 실패 유도)**

`RoleApiTest.java` — 이후 태스크(생성/수정/삭제/권한설정)에서 케이스를 계속 추가하므로, 공통 헬퍼를 갖춘 골격으로 만든다:
```java
package com.elipair.church.domain.role;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RoleApiTest {

    private static final Set<String> SEED_ROLES = Set.of("SUPER_ADMIN", "ADMIN", "MEMBER", "USER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private RoleRepository roleRepository;

    /** 시드 역할은 보존하고 테스트가 만든 역할만 제거. */
    @AfterEach
    void cleanup() {
        roleRepository.deleteAll(roleRepository.findAll().stream()
                .filter(r -> !SEED_ROLES.contains(r.getName()))
                .toList());
    }

    /** maxPriority·permissions를 가진 요청자 토큰. */
    private String token(int maxPriority, String... permissions) {
        return "Bearer "
                + provider.issueAccess(
                        new MemberPrincipal(1L, "uuid-admin", "관리자", maxPriority), null, List.of(permissions));
    }

    private String roleManager() {
        return token(1000, "ROLE_MANAGE");
    }

    @Test
    void lists_seed_roles_priority_desc_with_permissions() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header("Authorization", roleManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("SUPER_ADMIN")) // priority 1000 최상위
                .andExpect(jsonPath("$[0].permissions.length()").value(12))
                .andExpect(jsonPath("$.page").doesNotExist());
    }

    @Test
    void anonymous_is_401() throws Exception {
        mockMvc.perform(get("/api/admin/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void without_role_manage_is_403() throws Exception {
        mockMvc.perform(get("/api/admin/roles").header("Authorization", token(100, "SERMON_WRITE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}
```

- [ ] **Step 5: 테스트 실행해 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.role.RoleApiTest'`
Expected: PASS

- [ ] **Step 6: 커밋**
```bash
git add src/main/java/com/elipair/church/domain/role/dto/RoleResponse.java \
        src/main/java/com/elipair/church/domain/role/RoleService.java \
        src/main/java/com/elipair/church/domain/role/RoleController.java \
        src/test/java/com/elipair/church/domain/role/RoleApiTest.java
git commit -m "feat : 역할 목록 조회 API 추가 #7"
```

---

## Task 6: 역할 생성 API (`POST /api/admin/roles`)

**Files:**
- Create: `src/main/java/com/elipair/church/domain/role/dto/RoleCreateRequest.java`
- Modify: `src/main/java/com/elipair/church/domain/role/RoleService.java` (create + normalizeName + persist 추가)
- Modify: `src/main/java/com/elipair/church/domain/role/RoleController.java` (POST 추가)
- Test: `src/test/java/com/elipair/church/domain/role/RoleServiceTest.java` (신규)
- Modify: `src/test/java/com/elipair/church/domain/role/RoleApiTest.java` (생성 케이스 추가)

- [ ] **Step 1: RoleCreateRequest 작성**
```java
package com.elipair.church.domain.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoleCreateRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull Integer priority,
        @Size(max = 255) String description) {}
```

- [ ] **Step 2: RoleService.create + 헬퍼 작성**

`RoleService`에 import 추가: `com.elipair.church.domain.role.dto.RoleCreateRequest`, `com.elipair.church.global.exception.BusinessException`, `com.elipair.church.global.exception.ErrorCode`, `org.springframework.dao.DataIntegrityViolationException`.

`list()` 메서드 아래에 추가:
```java
    @Transactional
    public RoleResponse create(RoleCreateRequest request, int requesterMaxPriority) {
        String name = normalizeName(request.name());
        hierarchyValidator.validateAssignable(requesterMaxPriority, request.priority());
        if (roleRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        return persist(Role.create(name, request.priority(), normalizeDescription(request.description())));
    }

    // name UNIQUE 경합 백스톱(Position 패턴): 선검사를 빠져나간 동시 생성/수정을 saveAndFlush로 잡는다.
    private RoleResponse persist(Role role) {
        try {
            return RoleResponse.from(roleRepository.saveAndFlush(role));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "역할 이름은 필수입니다");
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "역할 이름은 공백일 수 없습니다");
        }
        return trimmed;
    }

    private String normalizeDescription(String raw) {
        return raw == null ? null : raw.trim();
    }
```

- [ ] **Step 3: RoleController.create 작성**

`RoleController`에 import 추가: `com.elipair.church.domain.role.dto.RoleCreateRequest`, `com.elipair.church.global.security.MemberPrincipal`, `jakarta.validation.Valid`, `org.springframework.http.HttpStatus`, `org.springframework.http.ResponseEntity`, `org.springframework.security.core.annotation.AuthenticationPrincipal`, `org.springframework.web.bind.annotation.PostMapping`, `org.springframework.web.bind.annotation.RequestBody`.

`list()` 아래에 추가:
```java
    @PostMapping
    public ResponseEntity<RoleResponse> create(
            @Valid @RequestBody RoleCreateRequest request, @AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, principal.maxPriority()));
    }
```

- [ ] **Step 4: RoleServiceTest 작성 (실패 유도)**

`RoleServiceTest.java`:
```java
package com.elipair.church.domain.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.role.dto.RoleCreateRequest;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.security.RoleHierarchyValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    // 실제 검증기 사용(순수 컴포넌트, <= 규칙).
    private final RoleHierarchyValidator hierarchyValidator = new RoleHierarchyValidator();

    private RoleService service() {
        return new RoleService(roleRepository, permissionRepository, hierarchyValidator);
    }

    @Test
    void create_forces_non_system_and_trims_name() {
        when(roleRepository.existsByName("EDITOR")).thenReturn(false);
        when(roleRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service().create(new RoleCreateRequest("  EDITOR  ", 500, "편집자"), 1000);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("EDITOR");
        assertThat(captor.getValue().isSystem()).isFalse();
    }

    @Test
    void create_allows_equal_priority_to_requester() {
        when(roleRepository.existsByName("PEER")).thenReturn(false);
        when(roleRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // 같은 레벨(900) 허용
        service().create(new RoleCreateRequest("PEER", 900, "동급"), 900);

        verify(roleRepository).saveAndFlush(any());
    }

    @Test
    void create_rejects_priority_above_requester() {
        assertThatThrownBy(() -> service().create(new RoleCreateRequest("HIGH", 901, "상위"), 900))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED));
        verify(roleRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_blank_name_throws_invalid_input() {
        assertThatThrownBy(() -> service().create(new RoleCreateRequest("   ", 500, "x"), 1000))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(roleRepository, never()).existsByName(any());
    }

    @Test
    void create_duplicate_name_throws() {
        when(roleRepository.existsByName("EDITOR")).thenReturn(true);

        assertThatThrownBy(() -> service().create(new RoleCreateRequest("EDITOR", 500, "x"), 1000))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
        verify(roleRepository, never()).saveAndFlush(any());
    }
}
```

- [ ] **Step 5: 서비스 테스트 실행해 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.role.RoleServiceTest'`
Expected: PASS

- [ ] **Step 6: RoleApiTest에 생성 케이스 추가**

`RoleApiTest`에 import 추가: `static ...MockMvcRequestBuilders.post`, `com.jayway.jsonpath.JsonPath`, `java.nio.charset.StandardCharsets`, `org.springframework.http.MediaType`.

클래스 안에 헬퍼 + 테스트 추가:
```java
    private long createRole(String name, int priority) throws Exception {
        String json = mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"priority\":" + priority + ",\"description\":\"d\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void create_returns_201_non_system_empty_permissions() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"EDITOR\",\"priority\":500,\"description\":\"편집자\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("EDITOR"))
                .andExpect(jsonPath("$.isSystem").value(false))
                .andExpect(jsonPath("$.permissions.length()").value(0));
    }

    @Test
    void create_priority_above_requester_is_403() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", token(900, "ROLE_MANAGE"))
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TOOHIGH\",\"priority\":901,\"description\":\"d\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void create_duplicate_name_is_409() throws Exception {
        createRole("EDITOR", 500);

        mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"EDITOR\",\"priority\":600,\"description\":\"d\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_RESOURCE"));
    }

    @Test
    void create_blank_name_is_400() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"priority\":500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }
```

- [ ] **Step 7: API 테스트 실행해 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.role.RoleApiTest'`
Expected: PASS

- [ ] **Step 8: 커밋**
```bash
git add src/main/java/com/elipair/church/domain/role/dto/RoleCreateRequest.java \
        src/main/java/com/elipair/church/domain/role/RoleService.java \
        src/main/java/com/elipair/church/domain/role/RoleController.java \
        src/test/java/com/elipair/church/domain/role/RoleServiceTest.java \
        src/test/java/com/elipair/church/domain/role/RoleApiTest.java
git commit -m "feat : 역할 생성 API·위계 검증 추가 #7"
```

---

## Task 7: 역할 수정 API (`PATCH /api/admin/roles/{id}`)

**Files:**
- Create: `src/main/java/com/elipair/church/domain/role/dto/RoleUpdateRequest.java`
- Modify: `src/main/java/com/elipair/church/domain/role/RoleService.java` (update 추가)
- Modify: `src/main/java/com/elipair/church/domain/role/RoleController.java` (PATCH 추가)
- Modify: `src/test/java/com/elipair/church/domain/role/RoleServiceTest.java`
- Modify: `src/test/java/com/elipair/church/domain/role/RoleApiTest.java`

- [ ] **Step 1: RoleUpdateRequest 작성**
```java
package com.elipair.church.domain.role.dto;

import jakarta.validation.constraints.Size;

public record RoleUpdateRequest(
        @Size(max = 50) String name, Integer priority, @Size(max = 255) String description) {}
```

- [ ] **Step 2: RoleService.update 작성**

`RoleService`에 import 추가: `com.elipair.church.domain.role.dto.RoleUpdateRequest`. `create` 메서드 아래에 추가:
```java
    @Transactional
    public RoleResponse update(Long id, RoleUpdateRequest request, int requesterMaxPriority) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        hierarchyValidator.validateMutable(requesterMaxPriority, role.getPriority(), role.isSystem());

        String name = null;
        if (request.name() != null) {
            name = normalizeName(request.name());
            if (!name.equals(role.getName()) && roleRepository.existsByName(name)) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
            }
        }
        if (request.priority() != null) {
            hierarchyValidator.validateAssignable(requesterMaxPriority, request.priority());
        }
        role.update(name, request.priority(), normalizeDescription(request.description()));
        return persist(role);
    }
```

- [ ] **Step 3: RoleController.update 작성**

import 추가: `org.springframework.web.bind.annotation.PatchMapping`, `org.springframework.web.bind.annotation.PathVariable`, `com.elipair.church.domain.role.dto.RoleUpdateRequest`. `create` 아래에 추가:
```java
    @PatchMapping("/{id}")
    public RoleResponse update(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return service.update(id, request, principal.maxPriority());
    }
```

- [ ] **Step 4: RoleServiceTest에 update 케이스 추가 (실패 유도)**

import 추가: `com.elipair.church.domain.role.dto.RoleUpdateRequest`, `java.util.Optional`. 클래스에 추가:
```java
    @Test
    void update_partial_name_only_keeps_priority() {
        Role role = Role.create("EDITOR", 500, "편집자");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(roleRepository.existsByName("AUTHOR")).thenReturn(false);
        when(roleRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        RoleResponse result = service().update(1L, new RoleUpdateRequest("AUTHOR", null, null), 1000);

        assertThat(result.name()).isEqualTo("AUTHOR");
        assertThat(result.priority()).isEqualTo(500);
    }

    @Test
    void update_system_role_is_rejected() {
        Role system = Role.create("X", 100, "x");
        org.springframework.test.util.ReflectionTestUtils.setField(system, "isSystem", true);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service().update(1L, new RoleUpdateRequest("Y", null, null), 1000))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED));
        verify(roleRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_unknown_id_is_not_found() {
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(999L, new RoleUpdateRequest("Y", null, null), 1000))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void update_priority_above_requester_is_rejected() {
        Role role = Role.create("EDITOR", 500, "편집자");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service().update(1L, new RoleUpdateRequest(null, 901, null), 900))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED));
    }
```

> NOTE: `ReflectionTestUtils.setField(system, "isSystem", true)`로 시스템 역할을 시뮬레이션한다(엔티티에 시스템 역할 팩토리가 없으므로 — 시스템 역할은 시드로만 생성).

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.role.RoleServiceTest'`
Expected: PASS

- [ ] **Step 6: RoleApiTest에 PATCH 케이스 추가**

import 추가: `static ...MockMvcRequestBuilders.patch`. 클래스에 추가:
```java
    @Test
    void patch_updates_priority_within_level() throws Exception {
        long id = createRole("EDITOR", 500);

        mockMvc.perform(patch("/api/admin/roles/" + id)
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":700}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("EDITOR"))
                .andExpect(jsonPath("$.priority").value(700));
    }

    @Test
    void patch_system_role_is_403() throws Exception {
        long adminId = roleRepository.findAll().stream()
                .filter(r -> r.getName().equals("MEMBER"))
                .findFirst()
                .orElseThrow()
                .getId();
        // MEMBER는 non-system이라 위계만 통과하면 수정 가능하므로, is_system 거부는 ADMIN(시스템)으로 확인
        long systemId = roleRepository.findAll().stream()
                .filter(r -> r.getName().equals("ADMIN"))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(patch("/api/admin/roles/" + systemId)
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"변경시도\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void patch_unknown_id_is_404() throws Exception {
        mockMvc.perform(patch("/api/admin/roles/999999")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
```

> NOTE: `roleManager()`는 maxPriority 1000이라 ADMIN(900)도 위계상 수정 대상이지만, ADMIN은 `is_system=true`라 `validateMutable`이 먼저 거부 → 403. (`adminId`/`systemId` 중 `adminId`·`MEMBER` 줄은 사용 안 하면 제거 가능 — 핵심은 `systemId`.)

- [ ] **Step 7: API 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.role.RoleApiTest'`
Expected: PASS

- [ ] **Step 8: 커밋**
```bash
git add src/main/java/com/elipair/church/domain/role/dto/RoleUpdateRequest.java \
        src/main/java/com/elipair/church/domain/role/RoleService.java \
        src/main/java/com/elipair/church/domain/role/RoleController.java \
        src/test/java/com/elipair/church/domain/role/RoleServiceTest.java \
        src/test/java/com/elipair/church/domain/role/RoleApiTest.java
git commit -m "feat : 역할 수정 API 추가 #7"
```

---

## Task 8: 역할 삭제 API (`DELETE /api/admin/roles/{id}`)

**Files:**
- Modify: `src/main/java/com/elipair/church/domain/role/RoleService.java` (delete 추가)
- Modify: `src/main/java/com/elipair/church/domain/role/RoleController.java` (DELETE 추가)
- Modify: `src/test/java/com/elipair/church/domain/role/RoleServiceTest.java`
- Modify: `src/test/java/com/elipair/church/domain/role/RoleApiTest.java`

- [ ] **Step 1: RoleService.delete 작성**

`update` 메서드 아래에 추가:
```java
    @Transactional
    public void delete(Long id, int requesterMaxPriority) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        hierarchyValidator.validateMutable(requesterMaxPriority, role.getPriority(), role.isSystem());
        roleRepository.delete(role);
        // NOTE(#8): member_roles 도입 후, 회원에게 할당된 역할이면 삭제 전 409로 차단(블로킹 삭제)한다.
    }
```

- [ ] **Step 2: RoleController.delete 작성**

import 추가: `org.springframework.web.bind.annotation.DeleteMapping`, `org.springframework.web.bind.annotation.ResponseStatus`. `update` 아래에 추가:
```java
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal MemberPrincipal principal) {
        service.delete(id, principal.maxPriority());
    }
```

- [ ] **Step 3: RoleServiceTest에 delete 케이스 추가**
```java
    @Test
    void delete_non_system_within_level_calls_delete() {
        Role role = Role.create("EDITOR", 500, "편집자");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        service().delete(1L, 1000);

        verify(roleRepository).delete(role);
    }

    @Test
    void delete_system_role_is_rejected() {
        Role system = Role.create("X", 100, "x");
        org.springframework.test.util.ReflectionTestUtils.setField(system, "isSystem", true);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> service().delete(1L, 1000))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED));
        verify(roleRepository, never()).delete(any());
    }

    @Test
    void delete_unknown_id_is_not_found() {
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(999L, 1000))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
```

- [ ] **Step 4: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.role.RoleServiceTest'`
Expected: PASS

- [ ] **Step 5: RoleApiTest에 DELETE 케이스 추가**

import 추가: `static ...MockMvcRequestBuilders.delete`. 클래스에 추가:
```java
    @Test
    void delete_returns_204_and_removes() throws Exception {
        long id = createRole("EDITOR", 500);

        mockMvc.perform(delete("/api/admin/roles/" + id).header("Authorization", roleManager()))
                .andExpect(status().isNoContent());

        assertThat(roleRepository.existsById(id)).isFalse();
    }

    @Test
    void delete_system_role_is_403() throws Exception {
        long systemId = roleRepository.findAll().stream()
                .filter(r -> r.getName().equals("USER"))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(delete("/api/admin/roles/" + systemId).header("Authorization", roleManager()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
```

import 추가(파일 상단): `static org.assertj.core.api.Assertions.assertThat`.

- [ ] **Step 6: API 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.role.RoleApiTest'`
Expected: PASS

- [ ] **Step 7: 커밋**
```bash
git add src/main/java/com/elipair/church/domain/role/RoleService.java \
        src/main/java/com/elipair/church/domain/role/RoleController.java \
        src/test/java/com/elipair/church/domain/role/RoleServiceTest.java \
        src/test/java/com/elipair/church/domain/role/RoleApiTest.java
git commit -m "feat : 역할 삭제 API 추가 #7"
```

---

## Task 9: 권한 일괄 설정 API (`PUT /api/admin/roles/{id}/permissions`)

**Files:**
- Create: `src/main/java/com/elipair/church/domain/role/dto/RolePermissionsRequest.java`
- Modify: `src/main/java/com/elipair/church/domain/role/RoleService.java` (setPermissions 추가)
- Modify: `src/main/java/com/elipair/church/domain/role/RoleController.java` (PUT 추가)
- Modify: `src/test/java/com/elipair/church/domain/role/RoleServiceTest.java`
- Modify: `src/test/java/com/elipair/church/domain/role/RoleApiTest.java`

- [ ] **Step 1: RolePermissionsRequest 작성**
```java
package com.elipair.church.domain.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RolePermissionsRequest(
        @NotNull List<@NotBlank @Size(max = 50) String> permissions) {}
```

- [ ] **Step 2: RoleService.setPermissions 작성**

import 추가: `com.elipair.church.domain.role.dto.RolePermissionsRequest`, `java.util.LinkedHashSet`, `java.util.List`, `java.util.Set`. `delete` 아래에 추가:
```java
    @Transactional
    public RoleResponse setPermissions(Long id, RolePermissionsRequest request, int requesterMaxPriority) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        hierarchyValidator.validateMutable(requesterMaxPriority, role.getPriority(), role.isSystem());

        // 중복은 흡수, 미지 키만 거부.
        Set<String> names = new LinkedHashSet<>(request.permissions());
        List<Permission> found = permissionRepository.findByNameIn(names);
        if (found.size() != names.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "존재하지 않는 권한이 포함되어 있습니다");
        }
        role.replacePermissions(found);
        return RoleResponse.from(role);
    }
```

- [ ] **Step 3: RoleController.setPermissions 작성**

import 추가: `com.elipair.church.domain.role.dto.RolePermissionsRequest`, `org.springframework.web.bind.annotation.PutMapping`. `delete` 아래에 추가:
```java
    @PutMapping("/{id}/permissions")
    public RoleResponse setPermissions(
            @PathVariable Long id,
            @Valid @RequestBody RolePermissionsRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return service.setPermissions(id, request, principal.maxPriority());
    }
```

- [ ] **Step 4: RoleServiceTest에 setPermissions 케이스 추가**

import 추가: `com.elipair.church.domain.role.dto.RolePermissionsRequest`, `java.util.List`. 클래스에 추가:
```java
    @Test
    void setPermissions_replaces_with_known_keys() {
        Role role = Role.create("EDITOR", 500, "편집자");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findByNameIn(any()))
                .thenReturn(List.of(Permission.of("SERMON_WRITE", "설교"), Permission.of("NOTICE_WRITE", "공지")));

        RoleResponse result = service().setPermissions(
                1L, new RolePermissionsRequest(List.of("SERMON_WRITE", "NOTICE_WRITE")), 1000);

        assertThat(result.permissions()).extracting("name").containsExactlyInAnyOrder("SERMON_WRITE", "NOTICE_WRITE");
    }

    @Test
    void setPermissions_dedups_duplicate_request() {
        Role role = Role.create("EDITOR", 500, "편집자");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findByNameIn(any())).thenReturn(List.of(Permission.of("GALLERY_VIEW", "갤러리")));

        // 중복 요청이 미지 키 400으로 오인되지 않아야 함
        RoleResponse result = service().setPermissions(
                1L, new RolePermissionsRequest(List.of("GALLERY_VIEW", "GALLERY_VIEW")), 1000);

        assertThat(result.permissions()).extracting("name").containsExactly("GALLERY_VIEW");
    }

    @Test
    void setPermissions_unknown_key_is_invalid_input() {
        Role role = Role.create("EDITOR", 500, "편집자");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findByNameIn(any())).thenReturn(List.of(Permission.of("SERMON_WRITE", "설교")));

        assertThatThrownBy(() -> service().setPermissions(
                        1L, new RolePermissionsRequest(List.of("SERMON_WRITE", "NOPE")), 1000))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
```

- [ ] **Step 5: 서비스 테스트 통과 확인**

Run: `./gradlew test --tests 'com.elipair.church.domain.role.RoleServiceTest'`
Expected: PASS

- [ ] **Step 6: RoleApiTest에 PUT 케이스 추가**

import 추가: `static ...MockMvcRequestBuilders.put`. 클래스에 추가:
```java
    @Test
    void put_permissions_replaces_set() throws Exception {
        long id = createRole("EDITOR", 500);

        mockMvc.perform(put("/api/admin/roles/" + id + "/permissions")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"SERMON_WRITE\",\"NOTICE_WRITE\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(2));

        // 재설정으로 전체 교체 확인
        mockMvc.perform(put("/api/admin/roles/" + id + "/permissions")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"GALLERY_VIEW\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(1))
                .andExpect(jsonPath("$.permissions[0].name").value("GALLERY_VIEW"));
    }

    @Test
    void put_unknown_permission_is_400() throws Exception {
        long id = createRole("EDITOR", 500);

        mockMvc.perform(put("/api/admin/roles/" + id + "/permissions")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"NOPE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void put_permissions_on_system_role_is_403() throws Exception {
        long systemId = roleRepository.findAll().stream()
                .filter(r -> r.getName().equals("USER"))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(put("/api/admin/roles/" + systemId + "/permissions")
                        .header("Authorization", roleManager())
                        .characterEncoding(StandardCharsets.UTF_8)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"GALLERY_VIEW\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
```

- [ ] **Step 7: 전체 테스트 실행 + spotless 적용 후 커밋**

Run: `./gradlew spotlessApply && ./gradlew clean build`
Expected: BUILD SUCCESSFUL (전체 테스트 그린, palantir 포맷 적용)
```bash
git add src/main/java/com/elipair/church/domain/role/dto/RolePermissionsRequest.java \
        src/main/java/com/elipair/church/domain/role/RoleService.java \
        src/main/java/com/elipair/church/domain/role/RoleController.java \
        src/test/java/com/elipair/church/domain/role/RoleServiceTest.java \
        src/test/java/com/elipair/church/domain/role/RoleApiTest.java
git commit -m "feat : 역할 권한 일괄 설정 API 추가 #7"
```

---

## 셀프리뷰 결과 (작성자 점검)

**1. 스펙 커버리지:**
- §3.2 테이블(permissions·roles·role_permissions) → Task 2·3 ✓
- §3.3 시드(permission 12·role 4·매트릭스) → Task 2 + 검증 Task 3(RbacSeedIntegrityTest) ✓
- §4.3 위계 `<=` + is_system + 경로 인가 → Task 1, Task 6~9 ✓
- §4.4 식별자(영어 키/한글 description) → 시드 description 한글, name 영어; role name 영어강제 미적용(설계 D10) ✓
- §5.4 6개 엔드포인트(list·create·patch·delete·put permissions·get permissions) → Task 4~9 ✓
- 설계 D5(PUT=name 리스트)·D6(목록 평배열 priority desc)·D8(@ManyToMany)·D11(삭제 계약 #8 이연) ✓

**2. Placeholder 스캔:** 없음. 모든 코드/테스트/SQL 전문 포함, 실행 명령·기대결과 명시.

**3. 타입 정합성:** `RoleService(roleRepository, permissionRepository, hierarchyValidator)` 생성자, `create/update/delete/setPermissions(... , int requesterMaxPriority)` 시그니처, `RoleResponse.from`·`PermissionResponse.from`, `Role.create/update/replacePermissions`, `PermissionRepository.findByNameIn/findAllByOrderByNameAsc`, `RoleRepository.findAllByOrderByPriorityDesc/existsByName` — 태스크 전반 일관.

**주의(실행자용) — 빌드 그린 불변식:**
- Task 2(마이그레이션)와 Task 3(엔티티)은 **순서 고정**. 엔티티를 먼저 추가하면 `ddl-auto: validate`가 "missing table"로 모든 `@SpringBootTest`를 깬다(메모리 SB4 함정 ①). 마이그레이션(테이블) → 엔티티(매핑) 순서를 지킬 것.
- `RoleApiTest`의 `@AfterEach`는 **시드 역할(SUPER_ADMIN·ADMIN·MEMBER·USER)을 보존**하고 테스트 생성 역할만 삭제한다 — `RbacSeedIntegrityTest`와 Testcontainer를 공유하므로 시드를 지우면 안 된다.
- 빌드 전 `./gradlew spotlessApply`(palantir 포맷) 필수.
