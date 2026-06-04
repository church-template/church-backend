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
        assertThat(result.get(1).getPermissions())
                .extracting(Permission::getName)
                .containsExactly("GALLERY_VIEW");
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
