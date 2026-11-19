package com.elipair.church.domain.visibility;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.MemberPrincipal;
import com.elipair.church.global.tenant.TenantContext;
import com.elipair.testfixture.TwoChurches;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** 공개 범위 설정 API(#95): GET /api/visibility 공개, PUT /api/admin/visibility VISIBILITY_MANAGE, 교회별 캐시 격리. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TwoChurches.class})
class VisibilityApiTest {

    private static final String ALL_PUBLIC = """
            {"notices":"PUBLIC","events":"PUBLIC","bulletins":"PUBLIC","sermons":"PUBLIC","gallery":"PUBLIC"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider provider;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private TwoChurches churches;

    @BeforeEach
    @AfterEach
    void reset() {
        // 기본 교회는 마이그레이션 값으로 되돌리고, 격리 교회 A·B는 행이 없는 상태(코드 기본값)로 둔다.
        jdbc.sql("update content_visibility set level = case when domain in ('SERMON', 'GALLERY')"
                        + " then 'MEMBERS' else 'PUBLIC' end where church_id = 1")
                .update();
        jdbc.sql("delete from content_visibility where church_id in (?, ?)")
                .params(churches.aId(), churches.bId())
                .update();
        Set<String> keys = redis.keys("visibility::*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private String bearer(List<String> permissions) {
        return "Bearer " + provider.issueAccess(new MemberPrincipal(1L, "u", "n", 1000), null, permissions);
    }

    private String bearerIn(long churchId, List<String> permissions) {
        return TenantContext.callAs(churchId, () -> bearer(permissions));
    }

    @Test
    void get_is_open_without_token_and_returns_five_levels() throws Exception {
        mvc.perform(get("/api/visibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notices").value("PUBLIC"))
                .andExpect(jsonPath("$.events").value("PUBLIC"))
                .andExpect(jsonPath("$.bulletins").value("PUBLIC"))
                .andExpect(jsonPath("$.sermons").value("MEMBERS"))
                .andExpect(jsonPath("$.gallery").value("MEMBERS"));
    }

    @Test
    void put_anonymous_is_401() throws Exception {
        mvc.perform(put("/api/admin/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ALL_PUBLIC))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    void put_without_visibility_manage_is_403() throws Exception {
        mvc.perform(put("/api/admin/visibility")
                        .header("Authorization", bearer(List.of("NOTICE_WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ALL_PUBLIC))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void put_replaces_all_five_and_get_reflects_it_immediately() throws Exception {
        mvc.perform(get("/api/visibility")).andExpect(jsonPath("$.sermons").value("MEMBERS")); // 캐시를 채운다

        mvc.perform(put("/api/admin/visibility")
                        .header("Authorization", bearer(List.of("VISIBILITY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notices":"MEMBERS","events":"MEMBERS","bulletins":"MEMBERS",
                                 "sermons":"PUBLIC","gallery":"PUBLIC"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notices").value("MEMBERS"))
                .andExpect(jsonPath("$.gallery").value("PUBLIC"));

        mvc.perform(get("/api/visibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notices").value("MEMBERS"))
                .andExpect(jsonPath("$.events").value("MEMBERS"))
                .andExpect(jsonPath("$.bulletins").value("MEMBERS"))
                .andExpect(jsonPath("$.sermons").value("PUBLIC"))
                .andExpect(jsonPath("$.gallery").value("PUBLIC"));
    }

    @Test
    void put_with_missing_field_is_400() throws Exception {
        mvc.perform(put("/api/admin/visibility")
                        .header("Authorization", bearer(List.of("VISIBILITY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"notices":"PUBLIC","events":"PUBLIC","bulletins":"PUBLIC","sermons":"PUBLIC"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
        mvc.perform(get("/api/visibility")).andExpect(jsonPath("$.sermons").value("MEMBERS")); // 저장되지 않았다
    }

    @Test
    void put_with_unknown_level_is_400() throws Exception {
        mvc.perform(put("/api/admin/visibility")
                        .header("Authorization", bearer(List.of("VISIBILITY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ALL_PUBLIC.replace("\"gallery\":\"PUBLIC\"", "\"gallery\":\"FRIENDS\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void changing_one_church_does_not_change_another() throws Exception {
        // 두 교회 모두 행이 없다 → 새 교회 기본값(갤러리만 MEMBERS). B의 캐시를 먼저 채운다.
        mvc.perform(get(churches.bPrefix() + "/api/visibility"))
                .andExpect(jsonPath("$.notices").value("PUBLIC"));

        mvc.perform(put(churches.aPrefix() + "/api/admin/visibility")
                        .header("Authorization", bearerIn(churches.aId(), List.of("VISIBILITY_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ALL_PUBLIC.replace("\"notices\":\"PUBLIC\"", "\"notices\":\"MEMBERS\"")))
                .andExpect(status().isOk());

        mvc.perform(get(churches.aPrefix() + "/api/visibility"))
                .andExpect(jsonPath("$.notices").value("MEMBERS"));
        mvc.perform(get(churches.bPrefix() + "/api/visibility"))
                .andExpect(jsonPath("$.notices").value("PUBLIC"))
                .andExpect(jsonPath("$.gallery").value("MEMBERS"));
        // 캐시를 비운 뒤(DB 기준)에도 B는 그대로다
        redis.delete(redis.keys("visibility::*"));
        mvc.perform(get(churches.bPrefix() + "/api/visibility"))
                .andExpect(jsonPath("$.notices").value("PUBLIC"));
        // 기본 교회도 그대로다
        mvc.perform(get("/api/visibility")).andExpect(jsonPath("$.notices").value("PUBLIC"));
    }
}
