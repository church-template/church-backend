package com.elipair.church.domain.visibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 공개 범위 마이그레이션(#95): 기존 교회의 설정 5행, 새 조회 권한 부여, 설정 권한 부여. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ContentVisibilityMigrationTest {

    @Autowired
    private JdbcClient jdbc;

    private Map<String, String> levels(long churchId) {
        return jdbc
                .sql("select domain, level from content_visibility where church_id = ?")
                .param(churchId)
                .query((rs, i) -> Map.entry(rs.getString("domain"), rs.getString("level")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private long rolesHolding(String permission) {
        return jdbc.sql("select count(distinct rp.role_id) from role_permissions rp"
                        + " join permissions p on p.id = rp.permission_id where p.name = ?")
                .param(permission)
                .query(Long.class)
                .single();
    }

    private List<String> rolesOfChurchHolding(long churchId, String permission) {
        return jdbc.sql("select r.name from roles r join role_permissions rp on rp.role_id = r.id"
                        + " join permissions p on p.id = rp.permission_id"
                        + " where r.church_id = ? and p.name = ? order by r.name")
                .params(churchId, permission)
                .query(String.class)
                .list();
    }

    @Test
    void existing_church_keeps_sermon_and_gallery_members_only() {
        // 테스트 DB의 기존 교회(church_id = 1): 5행, 설교·갤러리만 MEMBERS
        assertThat(levels(1L))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "NOTICE", "PUBLIC",
                        "EVENT", "PUBLIC",
                        "BULLETIN", "PUBLIC",
                        "SERMON", "MEMBERS",
                        "GALLERY", "MEMBERS"));
    }

    @Test
    void every_role_holding_gallery_view_gets_the_three_new_view_permissions() {
        long galleryViewers = rolesHolding("GALLERY_VIEW");
        assertThat(galleryViewers).isPositive();
        assertThat(rolesHolding("NOTICE_VIEW")).isEqualTo(galleryViewers);
        assertThat(rolesHolding("EVENT_VIEW")).isEqualTo(galleryViewers);
        assertThat(rolesHolding("BULLETIN_VIEW")).isEqualTo(galleryViewers);
    }

    @Test
    void admin_roles_get_visibility_manage_and_member_does_not() {
        assertThat(rolesOfChurchHolding(1L, "VISIBILITY_MANAGE")).containsExactly("ADMIN", "SUPER_ADMIN");
    }

    @Test
    void members_only_columns_default_to_false() {
        assertThat(jdbc.sql("select table_name || ':' || is_nullable || ':' || column_default"
                                + " from information_schema.columns where table_schema = current_schema()"
                                + " and column_name = 'members_only' order by table_name")
                        .query(String.class)
                        .list())
                .containsExactly("gallery_albums:NO:false", "sermons:NO:false");
    }
}
