package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

class SecurityErrorWritersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void entry_point_writes_401_invalid_token() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/sermons");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationEntryPoint(mapper).commence(request, response, new BadCredentialsException("x"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"INVALID_TOKEN\"");
        assertThat(response.getContentAsString()).contains("\"instance\":\"/api/admin/sermons\"");
    }

    @Test
    void access_denied_handler_writes_403_access_denied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/gallery/albums");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAccessDeniedHandler(mapper).handle(request, response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"errorCode\":\"ACCESS_DENIED\"");
    }
}
