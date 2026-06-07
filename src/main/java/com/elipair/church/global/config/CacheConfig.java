package com.elipair.church.global.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Redis 캐시 추상화(스펙 §9). /api/main을 @Cacheable, 콘텐츠 CUD 시 @CacheEvict로 무효화.
 * TTL은 CACHE_TTL(초, 기본 60)로 주입 — 교회별 조정값(멀티-교회 템플릿: 하드코딩 금지).
 * SB4=Jackson 3이므로 값 직렬화기는 GenericJacksonJsonRedisSerializer(Jackson 3, spring-data-redis 4)를 쓴다.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private final long ttlSeconds;

    public CacheConfig(@Value("${cache.ttl:60}") long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // enableUnsafeDefaultTyping: 값에 @class 타입정보를 심어 final record(MainResponse)까지 정확히 역직렬화한다.
        // 캐시는 우리 코드가 직렬화한 값만 담는 내부 저장소이므로(외부 입력 역직렬화 아님) 허용 가능한 설정이다.
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        GenericJacksonJsonRedisSerializer.builder()
                                .enableUnsafeDefaultTyping()
                                .build()));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
