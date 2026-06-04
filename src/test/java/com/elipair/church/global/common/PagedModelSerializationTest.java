package com.elipair.church.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;

/** 목록 응답 표준이 스펙 §5 JSON({content, page:{size,number,totalElements,totalPages}})과 일치함을 고정한다. */
class PagedModelSerializationTest {

    @Test
    void pagedModel_serializes_to_spec_list_envelope() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        Page<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 10), 42);

        String json = objectMapper.writeValueAsString(new PagedModel<>(page));
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.get("content").size()).isEqualTo(2);
        assertThat(root.path("page").path("size").asInt()).isEqualTo(10);
        assertThat(root.path("page").path("number").asInt()).isEqualTo(0);
        assertThat(root.path("page").path("totalElements").asLong()).isEqualTo(42L);
        assertThat(root.path("page").path("totalPages").asLong()).isEqualTo(5L);
    }
}
