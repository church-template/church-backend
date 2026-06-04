package com.elipair.church.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * @Validated 검증 fail-fast 증명. file.max-size=0(또는 음수)·빈 upload-dir이면 컨텍스트 기동이 실패해야 한다.
 */
class FilePropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(EnableFileProps.class);

    @Test
    void context_fails_when_max_size_not_positive() {
        runner.withPropertyValues("file.upload-dir=/tmp/up", "file.base-url=http://x/api/media", "file.max-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void context_fails_when_upload_dir_blank() {
        runner.withPropertyValues("file.upload-dir=", "file.base-url=http://x/api/media", "file.max-size=1024")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void context_starts_with_valid_properties() {
        runner.withPropertyValues("file.upload-dir=/tmp/up", "file.base-url=http://x/api/media", "file.max-size=1024")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @EnableConfigurationProperties(FileProperties.class)
    @Configuration
    static class EnableFileProps {}
}
