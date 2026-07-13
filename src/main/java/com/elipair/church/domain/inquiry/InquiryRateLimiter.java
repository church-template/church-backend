package com.elipair.church.domain.inquiry;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 문의 등록 스팸 방어(이슈 #50). 공개 API라 최소한의 제동은 필요하다.
 * key=inquiry:rl:{ip}, INCR + 첫 카운트에만 TTL 1시간 → IP당 시간당 MAX_PER_HOUR건.
 * ponytail: CAPTCHA·honeypot 없이 IP 카운터만. 실제로 스팸이 뚫리면 그때 올린다.
 */
@Component
public class InquiryRateLimiter {

    static final String PREFIX = "inquiry:rl:";
    private static final int MAX_PER_HOUR = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    public InquiryRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String clientIp) {
        String key = PREFIX + clientIp;
        Long count = redis.opsForValue().increment(key);
        if (count == null) {
            return; // Redis 응답 이상 — 문의 접수를 막지 않는다(가용성 우선)
        }
        if (count == 1L) {
            redis.expire(key, WINDOW);
        }
        if (count > MAX_PER_HOUR) {
            throw new BusinessException(
                    ErrorCode.RATE_LIMIT_EXCEEDED, "문의는 1시간에 " + MAX_PER_HOUR + "건까지 등록할 수 있습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
