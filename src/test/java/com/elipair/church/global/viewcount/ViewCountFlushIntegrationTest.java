package com.elipair.church.global.viewcount;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.sermon.Sermon;
import com.elipair.church.domain.sermon.SermonRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ViewCountFlushIntegrationTest {

    @Autowired
    private ViewCountStore store;

    @Autowired
    private ViewCountFlushScheduler scheduler;

    @Autowired
    private SermonRepository sermonRepository;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        sermonRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void flush_applies_buffered_views_to_db_and_clears_buffer() {
        Sermon s = sermonRepository.saveAndFlush(
                Sermon.create("설교", "김목사", "s", "마5", "본문", null, null, LocalDate.of(2026, 6, 1)));
        store.increment("sermon", s.getId());
        store.increment("sermon", s.getId());
        store.increment("sermon", s.getId());

        scheduler.flush();

        assertThat(sermonRepository
                        .findByIdAndDeletedAtIsNull(s.getId())
                        .orElseThrow()
                        .getViewCount())
                .isEqualTo(3L);
        // 버퍼가 비워져 재플러시는 변화 없음
        scheduler.flush();
        assertThat(sermonRepository
                        .findByIdAndDeletedAtIsNull(s.getId())
                        .orElseThrow()
                        .getViewCount())
                .isEqualTo(3L);
    }
}
