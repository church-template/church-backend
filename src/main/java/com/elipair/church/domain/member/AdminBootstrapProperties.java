package com.elipair.church.domain.member;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 최초 SUPER_ADMIN 부트스트랩 자격(.env 주입). 값이 비면 부트스트랩을 건너뛴다. */
@ConfigurationProperties("admin.bootstrap")
public record AdminBootstrapProperties(String phone, String name, String password) {

    boolean isComplete() {
        return hasText(phone) && hasText(name) && hasText(password);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
