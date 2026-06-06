package com.elipair.church.domain.event.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 일정 등록(POST) 요청. @Size(max)는 V9 컬럼 길이와 일치. description은 TEXT지만 스펙 §5 최소검증 상한. allDay 미지정 시 false. */
public record EventCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50000) String description,
        @Size(max = 200) String location,
        @NotNull LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean allDay,
        List<Long> tagIds) {

    /** end_at 배타 — start_at보다 엄격히 이후(또는 null=점 이벤트). end==start·end<start 거부(설계 §5.1). */
    @AssertTrue(message = "종료 일시는 시작 일시 이후여야 합니다")
    public boolean isEndAfterStart() {
        return endAt == null || startAt == null || endAt.isAfter(startAt);
    }
}
