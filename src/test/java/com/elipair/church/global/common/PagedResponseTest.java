package com.elipair.church.global.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elipair.church.global.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * WebConfig의 VIA_DTO 전역 직렬화 경로 검증: 컨트롤러가 Page&lt;T&gt;를 반환하면
 * 스펙 §5 목록 봉투({content, page:{size,number,totalElements,totalPages}})로 직렬화되는지 확인한다.
 * (라이브러리 PagedModel 자체가 아니라 WebConfig의 @EnableSpringDataWebSupport(VIA_DTO)가 적용되는지를 검증.)
 */
@WebMvcTest(controllers = PageTestController.class)
@Import(WebConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class PagedResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void controller_page_return_serializes_to_spec_list_envelope() throws Exception {
        mockMvc.perform(get("/test/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(42))
                .andExpect(jsonPath("$.page.totalPages").value(5));
    }
}
