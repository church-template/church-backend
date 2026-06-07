package com.elipair.church.global.viewcount;

import java.util.HashMap;
import java.util.Map;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 조회수 버퍼(스펙 §9). 키 = view:{namespace}:{id}, 값 = 미플러시 누적 조회수.
 * 상세조회는 increment(원자 INCR)로 카운팅하고, 주기 잡이 drain(SCAN+GETDEL)으로 빼 DB에 반영한다.
 * KEYS 금지(Redis keyspace 공유) → SCAN 사용. GETDEL(Redis 6.2+)로 get+삭제를 원자화해 유실/중복을 막는다.
 */
@Component
public class ViewCountStore {

    static final String PREFIX = "view:";

    private final StringRedisTemplate redis;

    public ViewCountStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(String namespace, long id) {
        return PREFIX + namespace + ":" + id;
    }

    /** 조회수 +1. 증가 후의 미플러시 누적값을 반환(라이브 표시용). */
    public long increment(String namespace, long id) {
        Long value = redis.opsForValue().increment(key(namespace, id));
        return value == null ? 0L : value;
    }

    /** 지정 delta만큼 버퍼 증가. 플러시(drain) 후 DB 반영 실패 시 드레인분을 버퍼에 되돌리는 복구용. */
    public long incrementBy(String namespace, long id, long delta) {
        Long value = redis.opsForValue().increment(key(namespace, id), delta);
        return value == null ? 0L : value;
    }

    /** 해당 namespace의 모든 버퍼를 원자적으로 비우고(id → delta) 반환. */
    public Map<Long, Long> drain(String namespace) {
        String prefix = PREFIX + namespace + ":";
        ScanOptions options =
                ScanOptions.scanOptions().match(prefix + "*").count(100).build();
        Map<Long, Long> deltas = new HashMap<>();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(key -> {
                String value = redis.opsForValue().getAndDelete(key); // GETDEL: 원자 get+삭제
                if (value != null) {
                    long id = Long.parseLong(key.substring(prefix.length()));
                    deltas.merge(id, Long.parseLong(value), Long::sum);
                }
            });
        }
        return deltas;
    }
}
