package com.elipair.church.domain.event;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 공개 목록의 날짜 범위(반열림 [from, toExclusive)). 파라미터 → 구간 해석·검증을 한곳에 모은다(설계 §3.1·§6.1).
 * year+month 또는 startDate+endDate 한 쌍을 받아 구간을 만든다. 둘 다 없으면 null(범위 없음, 전체).
 * 잘못된 입력(쌍 누락·year/month 범위 밖·endDate<startDate)은 INVALID_INPUT_VALUE.
 * 동시 제공 시 year/month 우선(설계 §6.1).
 */
public record DateRange(LocalDateTime from, LocalDateTime toExclusive) {

    public static DateRange resolve(Integer year, Integer month, LocalDate startDate, LocalDate endDate) {
        boolean hasYearMonth = year != null || month != null;
        boolean hasDateRange = startDate != null || endDate != null;

        if (hasYearMonth && (year == null || month == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (hasDateRange && (startDate == null || endDate == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (year != null && month != null) { // year/month 우선
            if (year < 1 || year > 9999 || month < 1 || month > 12) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            LocalDate first = LocalDate.of(year, month, 1);
            return new DateRange(first.atStartOfDay(), first.plusMonths(1).atStartOfDay());
        }
        if (startDate != null && endDate != null) {
            if (endDate.isBefore(startDate)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return new DateRange(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        }
        return null; // 범위 없음 → 전체
    }
}
