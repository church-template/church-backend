package com.elipair.church.domain.position.dto;

import com.elipair.church.domain.position.Position;
import java.time.LocalDateTime;

public record PositionResponse(Long id, String name, Integer sortOrder, LocalDateTime createdAt) {

    public static PositionResponse from(Position position) {
        return new PositionResponse(
                position.getId(), position.getName(), position.getSortOrder(), position.getCreatedAt());
    }
}
