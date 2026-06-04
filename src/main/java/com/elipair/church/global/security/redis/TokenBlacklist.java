package com.elipair.church.global.security.redis;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 로그아웃 access 토큰 블랙리스트(스펙 §4.1, G3 설계 "Redis 토큰 저장소 계약").
 * key=auth:blacklist:{jti}, value="1", TTL=토큰 남은 수명(expiresAt-now).
 * G3 필터는 isBlacklisted(read)만 호출. blacklist(write)는 D4 로그아웃이 호출한다.
 */
@Component
public class TokenBlacklist {

    static final String PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redis;

    public TokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void blacklist(String jti, Instant expiresAt) {
        long ttlSeconds = Duration.between(Instant.now(), expiresAt).toSeconds();
        if (ttlSeconds <= 0) {
            return; // 이미 만료된 토큰 — 저장 불필요
        }
        redis.opsForValue().set(PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}
