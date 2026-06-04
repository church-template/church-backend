package com.elipair.church.global.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화. createdAt/updatedAt는 자동 채워지고, createdBy/updatedBy는 AuditorAware가 공급한다.
 * 현재는 SecurityContext가 없어 빈 값을 반환하는 스텁이며, #4(보안 기반)에서 인증 회원의 id를
 * 반환하도록 본문만 교체한다("배관은 지금, 물은 #4").
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaConfig {

    @Bean
    public AuditorAware<Long> auditorAware() {
        return Optional::empty;
    }
}
