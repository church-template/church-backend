package com.elipair.church.domain.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TemporaryPasswordsTest {

    @Test
    void generates_min_length_random_password() {
        String a = TemporaryPasswords.generate();
        String b = TemporaryPasswords.generate();

        assertThat(a).hasSizeGreaterThanOrEqualTo(8);
        assertThat(a).isNotEqualTo(b); // 무작위(충돌 확률 무시 가능)
        assertThat(a).matches("[A-Za-z0-9]+"); // 혼동 없는 안전 문자집합
    }
}
