package com.elipair.church.domain.event.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 일정 전체 수정(PUT) 요청. version은 낙관락 비교용 필수. allDay null은 false로 간주(전체 교체). */
public record EventUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 50000) String description,
        @Size(max = 200) String location,
        @NotNull LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean allDay,
        List<Long> tagIds,
        @NotNull Long version) {

    @AssertTrue(message = "종료 일시는 시작 일시 이후여야 합니다")
    public boolean isEndAfterStart() {
        return endAt == null || startAt == null || endAt.isAfter(startAt);
    }
}
