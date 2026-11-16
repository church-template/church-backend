package com.elipair.church.domain.media;

import com.elipair.church.global.security.JwtProperties;
import com.elipair.church.global.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 미디어 서명 주소(`/api/media/{id}?exp=…&sig=…`). 서명 입력은 `churchId:mediaId:exp`, 키는 JWT_SECRET에서
 * HMAC-SHA256(secret, "media-url")로 파생해 토큰 서명 키와 분리한다. 교회 id가 입력에 들어가므로 다른 교회에서는 검증되지 않는다.
 */
@Component
public class MediaUrlSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final long TTL_SECONDS = 3600;

    private final byte[] key;
    private final Clock clock;

    public MediaUrlSigner(JwtProperties jwt, Clock clock) {
        this.key = hmac(jwt.secret().getBytes(StandardCharsets.UTF_8), "media-url".getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String signedPath(long mediaId) {
        long exp = clock.instant().getEpochSecond() + TTL_SECONDS;
        return "/api/media/" + mediaId + "?exp=" + exp + "&sig=" + sign(TenantContext.require(), mediaId, exp);
    }

    public boolean verify(long mediaId, long exp, String sig) {
        if (sig == null || exp <= clock.instant().getEpochSecond()) {
            return false;
        }
        byte[] expected = sign(TenantContext.require(), mediaId, exp).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(long churchId, long mediaId, long exp) {
        byte[] mac = hmac(key, (churchId + ":" + mediaId + ":" + exp).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e); // HmacSHA256은 JDK 필수 알고리즘이라 도달하지 않는다
        }
    }
}
