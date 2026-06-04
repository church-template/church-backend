package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * @Validated + @Positive 검증 fail-fast 증명.
 * jwt.access-expiry=0(또는 음수)이면 컨텍스트 기동이 실패해야 한다.
 */
class JwtPropertiesValidationTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(EnableJwtProps.class);

    @Test
    void context_fails_when_access_expiry_not_positive() {
        runner.withPropertyValues("jwt.secret=" + SECRET, "jwt.access-expiry=0", "jwt.refresh-expiry=1209600")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void context_starts_with_positive_expiry() {
        runner.withPropertyValues("jwt.secret=" + SECRET, "jwt.access-expiry=3600", "jwt.refresh-expiry=1209600")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void context_fails_when_secret_blank() {
        runner.withPropertyValues("jwt.secret=", "jwt.access-expiry=3600", "jwt.refresh-expiry=1209600")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(JwtProperties.class)
    @Configuration
    static class EnableJwtProps {}
}
