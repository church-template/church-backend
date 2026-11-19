package com.elipair.church.domain.main;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.event.EventRepository;
import com.elipair.church.domain.main.dto.MainResponse;
import com.elipair.church.domain.notice.NoticeRepository;
import com.elipair.church.domain.sermon.Sermon;
import com.elipair.church.domain.sermon.SermonRepository;
import com.elipair.church.domain.sermon.SermonService;
import com.elipair.church.domain.sermon.dto.SermonCardResponse;
import com.elipair.church.domain.sermon.dto.SermonCreateRequest;
import com.elipair.church.global.tenant.TenantContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * /api/main Redis 캐싱 검증(스펙 §9). 관찰 가능한 데이터(설교 수)로 캐시 hit/evict를 검증한다.
 * 2회차 getMain은 캐시에서 역직렬화되므로 Jackson 3 + record + LocalDate round-trip도 함께 보장된다(실패 시 예외).
 * 무효화는 RedisCache의 SCAN 기반 clean이 전파 지연을 가질 수 있어 짧은 폴링으로 결정성을 확보한다(운영은 CUD→조회 간 지연이 존재).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MainCacheTest {

    @Autowired
    private MainService mainService;

    @Autowired
    private SermonService sermonService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private SermonRepository sermonRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @AfterEach
    void cleanup() {
        cacheManager.getCache("main").clear();
        sermonRepository.deleteAll();
        noticeRepository.deleteAll();
        eventRepository.deleteAll();
    }

    private Sermon sermon(String title, int day) {
        return Sermon.create(title, "김목사", "s", "마5", "본문", null, null, LocalDate.of(2026, 6, day));
    }

    @Test
    void getMain_is_cached_and_cud_evicts() throws InterruptedException {
        sermonRepository.saveAndFlush(sermon("A", 1));
        assertThat(mainService.getMain().sermons()).hasSize(1); // 1회차: 캐시 적재 [A]

        // 서비스 우회로 DB에 직접 추가 → 캐시가 살아있으면 getMain은 여전히 [A]
        sermonRepository.saveAndFlush(sermon("B", 2));
        List<SermonCardResponse> cached = mainService.getMain().sermons();
        assertThat(cached).hasSize(1); // 캐시 히트(round-trip 성공)
        // 카드에 더한 membersOnly·version(#95)도 캐시 왕복에서 살아남는다 — 빠지면 version이 null로 돌아온다
        assertThat(cached.getFirst().version()).isEqualTo(0L);
        assertThat(cached.getFirst().membersOnly()).isFalse();

        // 서비스 CUD → @CacheEvict("main")
        sermonService.create(new SermonCreateRequest(
                "C", "김목사", "s", "마5", "본문", null, null, LocalDate.of(2026, 6, 3), List.of(), null));

        // 무효화 전파 후 재계산 → A·B·C 모두 반영(캐시 히트는 재적재하지 않으므로 폴링이 수렴한다)
        int size = 0;
        for (int i = 0; i < 40 && size != 3; i++) {
            size = mainService.getMain().sermons().size();
            if (size != 3) {
                Thread.sleep(25);
            }
        }
        assertThat(size).isEqualTo(3);
    }

    @Test
    void old_shape_cache_entry_is_treated_as_miss_and_overwritten() {
        sermonRepository.saveAndFlush(sermon("A", 1));
        MainResponse fresh = mainService.getMain(); // 캐시가 실제로 쓰는 키를 만들게 한 번 적재

        // 이전 빌드가 쓴 값 재현: 지금 직렬화기로 만든 JSON에서 #95가 더한 membersOnly·version만 지운다(손으로 쓴 JSON이 아니다)
        RedisCache cache = (RedisCache) cacheManager.getCache("main");
        ByteBuffer current =
                cache.getCacheConfiguration().getValueSerializationPair().write(fresh);
        JsonNode tree = JsonMapper.builder()
                .build()
                .readTree(StandardCharsets.UTF_8.decode(current).toString());
        int stripped = stripNewSermonCardFields(tree);
        assertThat(stripped).isEqualTo(1); // 설교 카드 1장에서 지웠다 — 0이면 아래 검증이 아무것도 재현하지 못한다
        byte[] oldShape = tree.toString().getBytes(StandardCharsets.UTF_8);

        byte[] key;
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            Set<byte[]> keys = connection
                    .keyCommands()
                    .keys(("main::" + TenantContext.require() + "::*").getBytes(StandardCharsets.UTF_8));
            assertThat(keys).hasSize(1);
            key = keys.iterator().next();
            connection.stringCommands().set(key, oldShape);
        }

        // 캐시를 건너뛴 DB 추가 — 옛 값을 버리고 메서드가 다시 돌면 B까지 보인다
        sermonRepository.saveAndFlush(sermon("B", 2));
        assertThat(mainService.getMain().sermons()).hasSize(2);

        // 새 값으로 덮어써졌다: 다음 조회는 캐시 히트(C는 안 보인다)이고 저장된 값에 membersOnly가 있다
        sermonRepository.saveAndFlush(sermon("C", 3));
        List<SermonCardResponse> cached = mainService.getMain().sermons();
        assertThat(cached).hasSize(2);
        assertThat(cached.getFirst().version()).isEqualTo(0L);
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            assertThat(new String(connection.stringCommands().get(key), StandardCharsets.UTF_8))
                    .contains("\"membersOnly\"");
        }
    }

    /** @class가 설교 카드인 객체에서 membersOnly·version을 지우고 지운 카드 수를 센다. */
    private static int stripNewSermonCardFields(JsonNode node) {
        int count = 0;
        if (node instanceof ObjectNode object
                && object.path("@class").asString("").endsWith("SermonCardResponse")) {
            object.remove(List.of("membersOnly", "version"));
            count++;
        }
        for (JsonNode child : node) {
            count += stripNewSermonCardFields(child);
        }
        return count;
    }
}
