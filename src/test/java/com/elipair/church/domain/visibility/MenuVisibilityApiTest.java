package com.elipair.church.domain.visibility;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.visibility.dto.VisibilityResponse;
import com.elipair.church.domain.visibility.dto.VisibilityUpdateRequest;
import com.elipair.church.global.security.JwtProperties;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 메뉴 단위 공개 범위(#95): 5개 메뉴의 목록·상세가 교회 설정대로 열리고 닫힌다.
 * SecurityConfig의 /api/gallery/**·/api/sermons/** 고정 매처를 대신하므로, 그 매처가 주던 401/403/200도 여기서 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MenuVisibilityApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private VisibilityService visibility;

    @Autowired
    private JdbcClient jdbc;

    private final Map<ContentDomain, Long> ids = new EnumMap<>(ContentDomain.class);

    static Stream<Arguments> menus() {
        return Stream.of(
                Arguments.of(ContentDomain.NOTICE, "/api/notices"),
                Arguments.of(ContentDomain.EVENT, "/api/events"),
                Arguments.of(ContentDomain.BULLETIN, "/api/bulletins"),
                Arguments.of(ContentDomain.SERMON, "/api/sermons"),
                Arguments.of(ContentDomain.GALLERY, "/api/gallery/albums"));
    }

    private long insert(String sql) {
        return jdbc.sql(sql + " returning id").query(Long.class).single();
    }

    @BeforeEach
    void seed() {
        resetToMigrationDefaults();
        ids.put(
                ContentDomain.NOTICE,
                insert("insert into notices(title, content, created_at, church_id) values ('메뉴공지', 'x', now(), 1)"));
        ids.put(
                ContentDomain.EVENT,
                insert("insert into events(title, start_at, created_at, church_id) values ('메뉴일정', now(), now(), 1)"));
        ids.put(
                ContentDomain.BULLETIN,
                insert("insert into bulletins(title, service_date, created_at, church_id)"
                        + " values ('메뉴주보', current_date, now(), 1)"));
        ids.put(
                ContentDomain.SERMON,
                insert("insert into sermons(title, preacher, preached_at, content, created_at, church_id)"
                        + " values ('메뉴설교', 'p', current_date, 'x', now(), 1)"));
        ids.put(
                ContentDomain.GALLERY,
                insert("insert into gallery_albums(title, created_at, church_id) values ('메뉴앨범', now(), 1)"));
    }

    @AfterEach
    void cleanup() {
        resetToMigrationDefaults();
        Map<ContentDomain, String> tables = Map.of(
                ContentDomain.NOTICE, "notices",
                ContentDomain.EVENT, "events",
                ContentDomain.BULLETIN, "bulletins",
                ContentDomain.SERMON, "sermons",
                ContentDomain.GALLERY, "gallery_albums");
        ids.forEach((domain, id) -> jdbc.sql("delete from " + tables.get(domain) + " where id = ?")
                .param(id)
                .update());
        ids.clear();
    }

    /** 기존 교회 값(설교·갤러리 MEMBERS)으로 저장한다 — 다른 테스트가 보는 기본 교회 상태를 되돌린다. */
    private void resetToMigrationDefaults() {
        visibility.update(new VisibilityUpdateRequest(
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.PUBLIC,
                VisibilityLevel.MEMBERS,
                VisibilityLevel.MEMBERS));
    }

    private void set(ContentDomain domain, VisibilityLevel level) {
        VisibilityResponse now = visibility.get();
        Map<ContentDomain, VisibilityLevel> next = new EnumMap<>(ContentDomain.class);
        for (ContentDomain d : ContentDomain.values()) {
            next.put(d, d == domain ? level : now.levelOf(d));
        }
        visibility.update(new VisibilityUpdateRequest(
                next.get(ContentDomain.NOTICE),
                next.get(ContentDomain.EVENT),
                next.get(ContentDomain.BULLETIN),
                next.get(ContentDomain.SERMON),
                next.get(ContentDomain.GALLERY)));
    }

    private String bearer(List<String> permissions) {
        return "Bearer " + provider.issueAccess(new MemberPrincipal(1L, "u", "n", 100), null, permissions);
    }

    private String expiredBearer(List<String> permissions) {
        JwtTokenProvider expiring =
                new JwtTokenProvider(new JwtProperties(jwtProperties.secret(), -60, jwtProperties.refreshExpiry()));
        return "Bearer " + expiring.issueAccess(new MemberPrincipal(1L, "u", "n", 100), null, permissions);
    }

    @ParameterizedTest
    @MethodSource("menus")
    void members_menu_anonymous_is_401(ContentDomain domain, String path) throws Exception {
        set(domain, VisibilityLevel.MEMBERS);
        mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @ParameterizedTest
    @MethodSource("menus")
    void members_menu_without_view_permission_is_403(ContentDomain domain, String path) throws Exception {
        set(domain, VisibilityLevel.MEMBERS);
        mvc.perform(get(path).header("Authorization", bearer(List.of("MEDIA_MANAGE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @MethodSource("menus")
    void members_menu_with_view_permission_is_200(ContentDomain domain, String path) throws Exception {
        set(domain, VisibilityLevel.MEMBERS);
        mvc.perform(get(path).header("Authorization", bearer(List.of(domain.viewPermission()))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("menus")
    void public_menu_anonymous_is_200(ContentDomain domain, String path) throws Exception {
        set(domain, VisibilityLevel.PUBLIC);
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("menus")
    void public_menu_with_expired_token_is_401_invalid_token(ContentDomain domain, String path) throws Exception {
        set(domain, VisibilityLevel.PUBLIC);
        mvc.perform(get(path).header("Authorization", expiredBearer(List.of(domain.viewPermission()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @ParameterizedTest
    @MethodSource("menus")
    void public_menu_with_blank_bearer_is_200(ContentDomain domain, String path) throws Exception {
        set(domain, VisibilityLevel.PUBLIC);
        mvc.perform(get(path).header("Authorization", "Bearer ")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("menus")
    void detail_follows_menu_level(ContentDomain domain, String path) throws Exception {
        String detail = path + "/" + ids.get(domain);
        set(domain, VisibilityLevel.MEMBERS);
        mvc.perform(get(detail))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        set(domain, VisibilityLevel.PUBLIC);
        mvc.perform(get(detail)).andExpect(status().isOk());
    }

    @Test
    void default_seed_matches_todays_behaviour() throws Exception {
        // 설정을 건드리지 않은 기존 교회: 설교·갤러리는 교인 전용, 공지·일정·주보는 공개(배포 직후 동작이 같아야 한다)
        mvc.perform(get("/api/sermons"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        mvc.perform(get("/api/gallery/albums"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
        mvc.perform(get("/api/notices")).andExpect(status().isOk());
        mvc.perform(get("/api/events")).andExpect(status().isOk());
        mvc.perform(get("/api/bulletins")).andExpect(status().isOk());
    }
}
