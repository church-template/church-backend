package com.elipair.church.global.viewcount;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 조회수 버퍼를 주기적으로 DB에 반영한다(스펙 §9). VIEW_FLUSH_INTERVAL(ms, 기본 60000)마다 실행.
 * 등록된 모든 ViewCountFlushTarget에 대해 store.drain → target.applyDeltas. 크래시 시 최대 1주기 유실(허용).
 */
@Slf4j
@Component
public class ViewCountFlushScheduler {

    private final ViewCountStore store;
    private final List<ViewCountFlushTarget> targets;

    public ViewCountFlushScheduler(ViewCountStore store, List<ViewCountFlushTarget> targets) {
        this.store = store;
        this.targets = targets;
    }

    @Scheduled(fixedDelayString = "${view.flush-interval:60000}")
    public void flush() {
        for (ViewCountFlushTarget target : targets) {
            String namespace = target.namespace();
            Map<Long, Long> deltas = store.drain(namespace);
            if (deltas.isEmpty()) {
                continue;
            }
            try {
                target.applyDeltas(deltas);
                log.debug("조회수 플러시: namespace={}, rows={}", namespace, deltas.size());
            } catch (Exception e) {
                // 반영 실패 시 드레인분을 버퍼에 재적재해 다음 주기에 재시도(유실 방지).
                // applyDeltas는 @Transactional이라 실패 시 롤백 → 부분커밋 없음 → 전량 재적재가 안전.
                // try/catch로 target을 격리해 한 도메인 실패가 다른 도메인 플러시를 막지 않게 한다.
                deltas.forEach((id, delta) -> store.incrementBy(namespace, id, delta));
                log.error("조회수 플러시 실패 — 버퍼 재적재: namespace={}, rows={}", namespace, deltas.size(), e);
            }
        }
    }
}
