package com.elipair.church.domain.main;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import com.elipair.church.domain.event.EventRepository;
import com.elipair.church.domain.notice.NoticeRepository;
import com.elipair.church.domain.sermon.Sermon;
import com.elipair.church.domain.sermon.SermonRepository;
import com.elipair.church.domain.sermon.SermonService;
import com.elipair.church.domain.sermon.dto.SermonCreateRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;

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
        assertThat(mainService.getMain().sermons()).hasSize(1); // 캐시 히트(round-trip 성공)

        // 서비스 CUD → @CacheEvict("main")
        sermonService.create(
                new SermonCreateRequest("C", "김목사", "s", "마5", "본문", null, null, LocalDate.of(2026, 6, 3), List.of()));

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
}
