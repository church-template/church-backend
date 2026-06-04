package com.elipair.church.global.security.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TokenBlacklistTest {

    @Autowired
    TokenBlacklist blacklist;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void blacklisted_jti_is_detected_with_ttl() {
        String jti = "jti-" + Instant.now().toEpochMilli();
        blacklist.blacklist(jti, Instant.now().plusSeconds(120));

        assertThat(blacklist.isBlacklisted(jti)).isTrue();
        Long ttl = redis.getExpire("auth:blacklist:" + jti);
        assertThat(ttl).isBetween(100L, 120L);
    }

    @Test
    void unknown_jti_is_not_blacklisted() {
        assertThat(blacklist.isBlacklisted("never-stored")).isFalse();
    }

    @Test
    void already_expired_expiresAt_is_not_stored() {
        String jti = "expired-" + Instant.now().toEpochMilli();
        blacklist.blacklist(jti, Instant.now().minusSeconds(10));

        assertThat(blacklist.isBlacklisted(jti)).isFalse();
    }
}
