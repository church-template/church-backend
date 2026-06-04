package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class JwtPropertiesTest {

    @Test
    void binds_kebab_case_properties() {
        var source = new MapConfigurationPropertySource(Map.of(
                "jwt.secret", "s3cr3t",
                "jwt.access-expiry", "3600",
                "jwt.refresh-expiry", "1209600"));

        JwtProperties props =
                new Binder(source).bind("jwt", JwtProperties.class).get();

        assertThat(props.secret()).isEqualTo("s3cr3t");
        assertThat(props.accessExpiry()).isEqualTo(3600);
        assertThat(props.refreshExpiry()).isEqualTo(1209600);
    }
}
