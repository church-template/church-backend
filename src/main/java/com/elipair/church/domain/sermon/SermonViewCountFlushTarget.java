package com.elipair.church.domain.sermon;

import com.elipair.church.global.viewcount.ViewCountFlushTarget;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 설교 조회수 플러시 대상(스펙 §9). Redis 버퍼 누적분을 sermons.view_count에 +반영. */
@Component
public class SermonViewCountFlushTarget implements ViewCountFlushTarget {

    public static final String NAMESPACE = "sermon";

    private final SermonRepository repository;

    public SermonViewCountFlushTarget(SermonRepository repository) {
        this.repository = repository;
    }

    @Override
    public String namespace() {
        return NAMESPACE;
    }

    @Override
    @Transactional
    public void applyDeltas(Map<Long, Long> deltas) {
        deltas.forEach(repository::incrementViewCountBy);
    }
}
