package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemberPrincipalTest {

    @Test
    void holds_identity_and_max_priority() {
        MemberPrincipal p = new MemberPrincipal(7L, "a3f8-uuid", "홍길동", 900);

        assertThat(p.id()).isEqualTo(7L);
        assertThat(p.uuid()).isEqualTo("a3f8-uuid");
        assertThat(p.name()).isEqualTo("홍길동");
        assertThat(p.maxPriority()).isEqualTo(900);
    }
}
