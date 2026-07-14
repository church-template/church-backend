package com.elipair.church.domain.inquiry;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 문의 등록 스팸 방어(이슈 #50). 공개 API라 최소한의 제동은 필요하다.
 * key=inquiry:rl:{ip}, INCR + 최초 카운트에만 EXPIRE → IP당 시간당 MAX_PER_HOUR건.
 *
 * <p>INCR과 EXPIRE는 Lua로 원자 실행한다 — 두 커맨드로 나누면 그 사이 연결이 끊겼을 때 TTL 없는 키가 남아
 * 해당 IP가 영구 차단된다(이후엔 count != 1이라 TTL을 다시 걸 기회도 없다).
 *
 * <p>Redis 장애 시에는 fail-open — 문의 접수는 교회의 창구라 스팸 방어보다 가용성이 우선이다.
 *
 * <p>ponytail: CAPTCHA·honeypot 없이 IP 카운터만. 실제로 스팸이 뚫리면 그때 올린다.
 */
@Component
public class InquiryRateLimiter {

    static final String PREFIX = "inquiry:rl:";
    private static final int MAX_PER_HOUR = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    /** INCR 후 최초(=1)일 때만 TTL을 건다. 반환값은 증가 후 카운트. */
    private static final RedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private static final Logger log = LoggerFactory.getLogger(InquiryRateLimiter.class);

    private final StringRedisTemplate redis;

    public InquiryRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String clientIp) {
        Long count = increment(PREFIX + clientIp);
        if (count != null && count > MAX_PER_HOUR) {
            throw new BusinessException(
                    ErrorCode.RATE_LIMIT_EXCEEDED, "문의는 1시간에 " + MAX_PER_HOUR + "건까지 등록할 수 있습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /** Redis에 닿지 못하면 null — 호출부는 제한 없이 통과시킨다(fail-open). */
    private Long increment(String key) {
        try {
            return redis.execute(INCR_WITH_TTL, List.of(key), String.valueOf(WINDOW.toSeconds()));
        } catch (DataAccessException e) {
            log.warn("문의 레이트리밋 Redis 접근 실패 — 접수는 허용한다(fail-open). key={}", key, e);
            return null;
        }
    }
}
