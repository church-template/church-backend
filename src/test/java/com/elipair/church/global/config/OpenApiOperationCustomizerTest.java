package com.elipair.church.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiOperationCustomizerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void api_docs_expose_error_response_schema_and_common_error_on_operations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // ErrorResponse 스키마 노출
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                // 공개 목록 GET에 공통 400 응답 주입
                .andExpect(
                        jsonPath("$.paths['/api/sermons'].get.responses['400']").exists());
    }
}
