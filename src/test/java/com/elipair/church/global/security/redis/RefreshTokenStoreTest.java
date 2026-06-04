package com.elipair.church.global.security.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RefreshTokenStoreTest {

    @Autowired
    RefreshTokenStore store;

    @Test
    void save_then_valid_then_revoke() {
        String uuid = "u-" + Instant.now().toEpochMilli();
        store.save(uuid, "jti-A", Instant.now().plusSeconds(600));

        assertThat(store.isValid(uuid, "jti-A")).isTrue();

        store.revoke(uuid, "jti-A");
        assertThat(store.isValid(uuid, "jti-A")).isFalse();
    }

    @Test
    void mismatched_uuid_or_jti_is_invalid() {
        String uuid = "u2-" + Instant.now().toEpochMilli();
        store.save(uuid, "jti-A", Instant.now().plusSeconds(600));

        assertThat(store.isValid(uuid, "jti-OTHER")).isFalse();
        assertThat(store.isValid("u-other", "jti-A")).isFalse();
    }

    @Test
    void revokeAll_removes_every_device_session() {
        String uuid = "u3-" + Instant.now().toEpochMilli();
        store.save(uuid, "jti-phone", Instant.now().plusSeconds(600));
        store.save(uuid, "jti-tablet", Instant.now().plusSeconds(600));

        store.revokeAll(uuid);

        assertThat(store.isValid(uuid, "jti-phone")).isFalse();
        assertThat(store.isValid(uuid, "jti-tablet")).isFalse();
    }
}
