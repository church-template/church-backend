package com.elipair.church.global.viewcount;

import java.util.Map;

/**
 * 조회수 플러시 대상(SPI). 각 도메인(설교·공지)이 자기 namespace와 DB 반영 방법을 제공한다.
 * global → domain 역참조를 피하기 위한 인터페이스(스펙 §7 도메인→global 단방향).
 */
public interface ViewCountFlushTarget {

    /** Redis 버퍼 키의 namespace(예: "sermon", "notice"). */
    String namespace();

    /** id → 누적 delta를 DB view_count에 +반영한다. */
    void applyDeltas(Map<Long, Long> deltas);
}
