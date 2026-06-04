package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789"; // >=32 bytes
    private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 3600, 1209600));

    @Test
    void access_token_round_trips_all_claims() {
        MemberPrincipal principal = new MemberPrincipal(7L, "uuid-7", "홍길동", 900);

        String token = provider.issueAccess(principal, "장로", List.of("SERMON_WRITE", "NOTICE_WRITE"));
        Claims claims = provider.parse(token);

        assertThat(claims.getSubject()).isEqualTo("uuid-7");
        assertThat(claims.get(JwtTokenProvider.CLAIM_MID, Long.class)).isEqualTo(7L);
        assertThat(claims.get(JwtTokenProvider.CLAIM_NAME, String.class)).isEqualTo("홍길동");
        assertThat(claims.get(JwtTokenProvider.CLAIM_POSITION, String.class)).isEqualTo("장로");
        assertThat(claims.get(JwtTokenProvider.CLAIM_MAX_PRIORITY, Integer.class))
                .isEqualTo(900);
        assertThat(claims.get(JwtTokenProvider.CLAIM_PERMISSIONS, List.class))
                .containsExactly("SERMON_WRITE", "NOTICE_WRITE");
        assertThat(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class)).isEqualTo(JwtTokenProvider.TYPE_ACCESS);
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void refresh_token_is_minimal_and_typed() {
        String token = provider.issueRefresh("uuid-7");
        Claims claims = provider.parse(token);

        assertThat(claims.getSubject()).isEqualTo("uuid-7");
        assertThat(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class)).isEqualTo(JwtTokenProvider.TYPE_REFRESH);
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.get(JwtTokenProvider.CLAIM_PERMISSIONS, List.class)).isNull();
    }

    @Test
    void expired_token_is_rejected() {
        JwtTokenProvider expiring = new JwtTokenProvider(new JwtProperties(SECRET, -60, 1209600));
        String token = expiring.issueAccess(new MemberPrincipal(1L, "u", "n", 0), null, List.of());

        assertThatThrownBy(() -> provider.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tampered_signature_is_rejected() {
        String foreign = new JwtTokenProvider(
                        new JwtProperties("another-secret-another-secret-0123456789", 3600, 1209600))
                .issueAccess(new MemberPrincipal(1L, "u", "n", 0), null, List.of());

        assertThatThrownBy(() -> provider.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void weak_secret_fails_fast_at_construction() {
        assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("too-short", 3600, 1209600)))
                .isInstanceOf(WeakKeyException.class);
    }
}
