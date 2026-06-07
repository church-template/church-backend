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
            Map<Long, Long> deltas = store.drain(target.namespace());
            if (!deltas.isEmpty()) {
                target.applyDeltas(deltas);
                log.debug("조회수 플러시: namespace={}, rows={}", target.namespace(), deltas.size());
            }
        }
    }
}
