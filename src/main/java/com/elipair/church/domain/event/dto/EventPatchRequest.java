package com.elipair.church.domain.event.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 일정 부분 수정(PATCH) 요청. 전달된(비-null) 필드만 적용. tagIds null이면 태그 미변경. version 필수.
 * start/end 교차검증은 DB값과 합쳐야 가능하므로 서비스가 수행(설계 §5.1).
 */
public record EventPatchRequest(
        @Size(max = 200) String title,
        @Size(max = 50000) String description,
        @Size(max = 200) String location,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean allDay,
        List<Long> tagIds,
        @NotNull Long version) {}
