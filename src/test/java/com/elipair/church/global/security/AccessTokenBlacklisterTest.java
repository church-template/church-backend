package com.elipair.church.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.global.security.redis.TokenBlacklist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessTokenBlacklisterTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private TokenBlacklist tokenBlacklist;

    @InjectMocks
    private AccessTokenBlacklister blacklister;

    @Test
    void blacklists_jti_of_valid_access_token() {
        Claims claims = mock(Claims.class);
        when(tokenProvider.parse("acc")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 60_000));

        blacklister.blacklist("acc");

        verify(tokenBlacklist).blacklist(eq("jti-1"), any());
    }

    @Test
    void ignores_invalid_token() {
        when(tokenProvider.parse("bad")).thenThrow(new JwtException("bad"));

        blacklister.blacklist("bad");

        verify(tokenBlacklist, never()).blacklist(any(), any());
    }

    @Test
    void ignores_token_without_jti() {
        Claims claims = mock(Claims.class);
        when(tokenProvider.parse("no-jti")).thenReturn(claims);
        when(claims.getId()).thenReturn(null);

        blacklister.blacklist("no-jti");

        verify(tokenBlacklist, never()).blacklist(any(), any());
    }
}
