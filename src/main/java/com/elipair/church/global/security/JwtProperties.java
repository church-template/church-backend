package com.elipair.church.global.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 설정(스펙 §4·§10). 만료값은 초 단위. secret은 @NotBlank이어야 하며, HS256용 32바이트(256bit) 이상이어야 한다.
 *
 * <p>만료값은 @Positive로, secret은 @NotBlank로 기동 시 검증한다(0/음수·빈 값 주입 시 fail-fast).
 * 32바이트 미만 시크릿은 jjwt의 Keys.hmacShaKeyFor가 WeakKeyException으로 기동을 추가 거부한다.
 * 바인딩 시점에만 검증되므로(@Validated) 레코드를 직접 생성하는 단위 테스트에는 영향이 없다.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Positive long accessExpiry,
        @Positive long refreshExpiry) {}
