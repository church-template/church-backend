package com.elipair.church.domain.inquiry;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class InquiryRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @InjectMocks
    private InquiryRateLimiter rateLimiter;

    @Test
    void allows_request_when_under_limit() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(5L);

        assertThatCode(() -> rateLimiter.check("10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void rejects_request_over_limit() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(6L);

        assertThatThrownBy(() -> rateLimiter.check("10.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    /** Redis가 죽어도 문의 접수는 막지 않는다(가용성 우선) — 500이 아니라 통과여야 한다. */
    @Test
    void fails_open_when_redis_is_unreachable() {
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatCode(() -> rateLimiter.check("10.0.0.1")).doesNotThrowAnyException();
    }
}
