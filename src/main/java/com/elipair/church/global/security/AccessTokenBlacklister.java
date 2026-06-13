package com.elipair.church.global.security;

import com.elipair.church.global.security.redis.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;

/**
 * access 토큰을 파싱해 jti를 블랙리스트에 등록한다(로그아웃·회원탈퇴 공용).
 * 파싱 실패(만료/위조)는 방어적으로 무시 — 인증 필터를 통과한 토큰이라 도달이 드물다.
 */
@Component
public class AccessTokenBlacklister {

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklist tokenBlacklist;

    public AccessTokenBlacklister(JwtTokenProvider tokenProvider, TokenBlacklist tokenBlacklist) {
        this.tokenProvider = tokenProvider;
        this.tokenBlacklist = tokenBlacklist;
    }

    public void blacklist(String accessToken) {
        try {
            Claims claims = tokenProvider.parse(accessToken);
            if (claims.getId() != null) {
                tokenBlacklist.blacklist(claims.getId(), claims.getExpiration().toInstant());
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // 도달 드묾(필터 통과 토큰) — 방어적 무시
        }
    }
}
