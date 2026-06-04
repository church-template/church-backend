package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityAuditorAwareTest {

    private final SecurityAuditorAware auditorAware = new SecurityAuditorAware();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returns_member_id_when_authenticated() {
        MemberPrincipal principal = new MemberPrincipal(42L, "uuid", "name", 100);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThat(auditorAware.getCurrentAuditor()).contains(42L);
    }

    @Test
    void empty_when_unauthenticated() {
        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }
}
