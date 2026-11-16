package com.elipair.church.domain.department.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * PUT/PATCH의 id 칸 3상태 — 칸 없음(컴포넌트 null)=유지, {@code null}=비움(value null), 숫자=설정.
 * Optional은 "없음"과 "null"을 구분하지 못해 쓰지 않는다.
 */
@JsonDeserialize(using = IdChange.Deserializer.class)
@Schema(implementation = Long.class, nullable = true)
public record IdChange(Long value) {

    static class Deserializer extends ValueDeserializer<IdChange> {
        @Override
        public IdChange deserialize(JsonParser p, DeserializationContext ctxt) {
            return new IdChange(ctxt.readValue(p, Long.class)); // 숫자가 아니면 JacksonException → 400
        }

        @Override
        public IdChange getNullValue(DeserializationContext ctxt) {
            return new IdChange(null);
        }

        @Override
        public IdChange getAbsentValue(DeserializationContext ctxt) {
            return null;
        }
    }
}
