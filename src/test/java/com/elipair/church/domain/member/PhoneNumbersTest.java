package com.elipair.church.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elipair.church.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void strips_non_digits() {
        assertThat(PhoneNumbers.normalize("010-1234-5678")).isEqualTo("01012345678");
        assertThat(PhoneNumbers.normalize(" 010 1234 5678 ")).isEqualTo("01012345678");
        assertThat(PhoneNumbers.normalize("+82 10-1234-5678")).isEqualTo("821012345678");
    }

    @Test
    void rejects_null_or_empty_after_strip() {
        assertThatThrownBy(() -> PhoneNumbers.normalize(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PhoneNumbers.normalize("---")).isInstanceOf(BusinessException.class);
    }

    @Test
    void extract_digits_keeps_only_numbers() {
        assertThat(PhoneNumbers.extractDigits("010-1234-5678")).isEqualTo("01012345678");
        assertThat(PhoneNumbers.extractDigits("김철수")).isEmpty();
        assertThat(PhoneNumbers.extractDigits(null)).isEmpty();
        assertThat(PhoneNumbers.extractDigits("  ")).isEmpty();
    }
}
