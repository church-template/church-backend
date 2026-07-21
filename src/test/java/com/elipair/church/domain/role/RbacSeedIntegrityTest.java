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
    void seeds_eighteen_permissions() {
        assertThat(permissionRepository.findAllByOrderByNameAsc())
                .extracting(Permission::getName)
                .containsExactlyInAnyOrder(
                        "SERMON_WRITE",
                        "NOTICE_WRITE",
                        "EVENT_WRITE",
                        "DEPT_WRITE",
                        "MEMBER_MANAGE",
                        "ROLE_MANAGE",
                        "POSITION_MANAGE",
                        "MEDIA_MANAGE",
                        "TAG_MANAGE",
                        "GALLERY_WRITE",
                        "GALLERY_VIEW",
                        "BULLETIN_WRITE",
                        "CHALLENGE_MANAGE",
                        "CHALLENGE_PARTICIPATE",
                        "INQUIRY_MANAGE",
                        "SERMON_VIEW",
                        "VEHICLE_MANAGE",
                        "VEHICLE_APPLY");
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

        assertThat(byName.get("SUPER_ADMIN").getPermissions()).hasSize(18);
        assertThat(byName.get("ADMIN").getPermissions()).hasSize(18);
        assertThat(byName.get("MEMBER").getPermissions())
                .extracting(Permission::getName)
                .containsExactlyInAnyOrder("GALLERY_VIEW", "CHALLENGE_PARTICIPATE", "SERMON_VIEW", "VEHICLE_APPLY");
        assertThat(byName.get("USER").getPermissions()).isEmpty();
    }

    @Test
    void permission_findByNameIn_resolves_known_keys() {
        List<Permission> found = permissionRepository.findByNameIn(List.of("SERMON_WRITE", "GALLERY_VIEW", "NOPE"));
        assertThat(found).extracting(Permission::getName).containsExactlyInAnyOrder("SERMON_WRITE", "GALLERY_VIEW");
    }
}
