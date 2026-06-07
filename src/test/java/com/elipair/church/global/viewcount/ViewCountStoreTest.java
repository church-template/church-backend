package com.elipair.church.global.viewcount;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ViewCountStoreTest {

    @Autowired
    private ViewCountStore store;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanup() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void increment_returns_running_buffer_value() {
        assertThat(store.increment("sermon", 7L)).isEqualTo(1L);
        assertThat(store.increment("sermon", 7L)).isEqualTo(2L);
    }

    @Test
    void drain_returns_deltas_and_clears_keys() {
        store.increment("sermon", 7L);
        store.increment("sermon", 7L);
        store.increment("sermon", 9L);

        Map<Long, Long> deltas = store.drain("sermon");

        assertThat(deltas).containsEntry(7L, 2L).containsEntry(9L, 1L);
        // 비운 뒤 재증가는 1부터
        assertThat(store.increment("sermon", 7L)).isEqualTo(1L);
        assertThat(store.drain("sermon")).containsExactly(Map.entry(7L, 1L));
    }

    @Test
    void drain_isolates_namespaces() {
        store.increment("sermon", 1L);
        store.increment("notice", 1L);

        assertThat(store.drain("sermon")).containsExactly(Map.entry(1L, 1L));
        assertThat(store.drain("notice")).containsExactly(Map.entry(1L, 1L));
    }
}
