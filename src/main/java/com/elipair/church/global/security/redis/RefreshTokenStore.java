package com.elipair.church.global.security.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 다중 세션 Refresh 토큰 저장소(G3 설계 "Redis 토큰 저장소 계약").
 * key=auth:refresh:{uuid}:{jti}, value="1", TTL=refresh 남은 수명. 회원·기기별 독립 세션.
 * G3은 read(isValid)만 사용. save/revoke/revokeAll(write)은 D4 로그인·로그아웃이 호출한다.
 *
 * <p>revokeAll은 KEYS가 아니라 SCAN(커서 기반, 논블로킹)으로 키를 수집한다. Redis는 캐시·조회수 등과
 * keyspace를 공유하므로(스펙 §9) KEYS는 전체를 블로킹할 수 있다. SCAN은 그 위험을 피한다.
 */
@Component
public class RefreshTokenStore {

    static final String PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;

    public RefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(String uuid, String jti) {
        return PREFIX + uuid + ":" + jti;
    }

    public void save(String uuid, String jti, Instant expiresAt) {
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).toSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        redis.opsForValue().set(key(uuid, jti), "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isValid(String uuid, String jti) {
        return Boolean.TRUE.equals(redis.hasKey(key(uuid, jti)));
    }

    public void revoke(String uuid, String jti) {
        redis.delete(key(uuid, jti));
    }

    /** 전체 로그아웃·강제 만료(스펙 §4.1). SCAN으로 해당 회원의 세션 키만 수집해 삭제(KEYS 미사용 — 논블로킹). */
    public void revokeAll(String uuid) {
        ScanOptions options =
                ScanOptions.scanOptions().match(PREFIX + uuid + ":*").count(100).build();
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
