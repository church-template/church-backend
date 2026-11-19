package com.elipair.church.domain.visibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.testfixture.TwoChurches;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TwoChurches.class})
class VisibilityServiceTest {

    @Autowired
    private VisibilityService service;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private TwoChurches churches;

    @BeforeEach
    @AfterEach
    void reset() {
        jdbc.sql("delete from content_visibility where church_id = ?")
                .param(churches.aId())
                .update();
        Set<String> keys = redis.keys("visibility::*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void missing_row_falls_back_to_domain_default() {
        // 교회 A에는 설정 행이 없다(마이그레이션 뒤에 만든 교회) — 서버가 죽지 않고 새 교회 기본값으로 본다.
        assertThat(churches.inA(() -> service.levelOf(ContentDomain.GALLERY))).isEqualTo(VisibilityLevel.MEMBERS);
        assertThat(churches.inA(() -> service.levelOf(ContentDomain.SERMON))).isEqualTo(VisibilityLevel.PUBLIC);
        assertThat(churches.inA(() -> service.isPublic(ContentDomain.NOTICE))).isTrue();
        // 대조: 행이 있는 기본 교회는 저장된 값(설교 MEMBERS)을 읽는다
        assertThat(service.levelOf(ContentDomain.SERMON)).isEqualTo(VisibilityLevel.MEMBERS);
    }

    @Test
    void level_of_reads_cache_instead_of_db_until_saved() {
        assertThat(service.levelOf(ContentDomain.SERMON)).isEqualTo(VisibilityLevel.MEMBERS);
        // DB만 바꾸면 캐시가 그대로라 값이 바뀌지 않는다 — 요청마다 DB를 치지 않는다는 뜻
        jdbc.sql("update content_visibility set level = 'PUBLIC' where church_id = 1 and domain = 'SERMON'")
                .update();
        try {
            assertThat(service.levelOf(ContentDomain.SERMON)).isEqualTo(VisibilityLevel.MEMBERS);
        } finally {
            jdbc.sql("update content_visibility set level = 'MEMBERS' where church_id = 1 and domain = 'SERMON'")
                    .update();
        }
    }
}
