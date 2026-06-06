package com.elipair.church.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    @Test
    void year_month_builds_half_open_month_range() {
        DateRange r = DateRange.resolve(2026, 6, null, null);
        assertThat(r.from()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(r.toExclusive()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    @Test
    void start_end_builds_inclusive_end_half_open_range() {
        DateRange r = DateRange.resolve(null, null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        assertThat(r.from()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(r.toExclusive()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    @Test
    void no_params_returns_null() {
        assertThat(DateRange.resolve(null, null, null, null)).isNull();
    }

    @Test
    void year_month_takes_priority_over_date_range() {
        DateRange r = DateRange.resolve(2026, 6, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 2));
        assertThat(r.from()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
    }

    @Test
    void partial_year_month_is_rejected() {
        assertThat(badRequest(() -> DateRange.resolve(2026, null, null, null))).isTrue();
        assertThat(badRequest(() -> DateRange.resolve(null, 6, null, null))).isTrue();
    }

    @Test
    void partial_date_range_is_rejected() {
        assertThat(badRequest(() -> DateRange.resolve(null, null, LocalDate.of(2026, 6, 1), null)))
                .isTrue();
    }

    @Test
    void out_of_range_year_or_month_is_rejected() {
        assertThat(badRequest(() -> DateRange.resolve(0, 6, null, null))).isTrue();
        assertThat(badRequest(() -> DateRange.resolve(10000, 6, null, null))).isTrue();
        assertThat(badRequest(() -> DateRange.resolve(2026, 13, null, null))).isTrue();
    }

    @Test
    void end_before_start_is_rejected() {
        assertThat(badRequest(() -> DateRange.resolve(null, null, LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 1))))
                .isTrue();
    }

    private boolean badRequest(Runnable r) {
        try {
            r.run();
            return false;
        } catch (BusinessException e) {
            return e.getErrorCode() == ErrorCode.INVALID_INPUT_VALUE;
        }
    }
}
