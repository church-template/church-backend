package com.elipair.church.domain.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.global.security.JwtProperties;
import com.elipair.church.global.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediaUrlSignerTest {

    private static final Pattern PATH = Pattern.compile("^/api/media/(\\d+)\\?exp=(\\d+)&sig=([A-Za-z0-9_-]+)$");
    private static final Instant NOW = Instant.parse("2026-11-16T00:00:00Z");

    private final JwtProperties jwt = new JwtProperties("test-secret-test-secret-0123456789ab", 3600, 1209600);
    private final MediaUrlSigner signer = new MediaUrlSigner(jwt, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void church1() {
        TenantContext.set(1L);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private Matcher signed(long mediaId) {
        Matcher m = PATH.matcher(signer.signedPath(mediaId));
        assertThat(m.matches()).isTrue();
        return m;
    }

    @Test
    void 서명한_주소는_검증된다() {
        Matcher m = signed(5);
        assertThat(Long.parseLong(m.group(2))).isEqualTo(NOW.getEpochSecond() + 3600);
        assertThat(signer.verify(5, Long.parseLong(m.group(2)), m.group(3))).isTrue();
    }

    @Test
    void 다른_미디어_id면_실패() {
        Matcher m = signed(5);
        assertThat(signer.verify(6, Long.parseLong(m.group(2)), m.group(3))).isFalse();
    }

    @Test
    void exp를_바꾸면_실패() {
        Matcher m = signed(5);
        assertThat(signer.verify(5, Long.parseLong(m.group(2)) + 1, m.group(3))).isFalse();
    }

    @Test
    void 만료되면_실패() {
        Matcher m = signed(5);
        MediaUrlSigner later = new MediaUrlSigner(jwt, Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC));
        assertThat(later.verify(5, Long.parseLong(m.group(2)), m.group(3))).isFalse();
    }

    @Test
    void 다른_교회면_실패() {
        Matcher m = signed(5);
        TenantContext.set(2L);
        assertThat(signer.verify(5, Long.parseLong(m.group(2)), m.group(3))).isFalse();
    }
}
