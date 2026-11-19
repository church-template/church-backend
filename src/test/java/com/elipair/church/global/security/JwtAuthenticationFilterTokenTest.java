package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.elipair.church.global.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** 공개 메뉴의 "무효 토큰 401"(#95)을 위한 필터 규칙: 빈 Bearer는 토큰 없음, 무효 토큰은 요청에 표시를 남긴다. */
class JwtAuthenticationFilterTokenTest {

    private static final String SECRET = "filter-test-secret-filter-test-secret-0123";
    private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 3600, 1209600));
    private final JwtAuthenticationFilterTest.TokenBlacklistStub blacklist =
            new JwtAuthenticationFilterTest.TokenBlacklistStub();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, blacklist);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest run(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        return request;
    }

    private String access() {
        return provider.issueAccess(new MemberPrincipal(7L, "uuid-7", "n", 0), null, List.of("SERMON_VIEW"));
    }

    @Test
    void blank_bearer_is_treated_as_no_token() throws Exception {
        for (String header : List.of("Bearer ", "Bearer    ")) {
            MockHttpServletRequest request = run(header);
            assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE))
                    .as(header)
                    .isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    @Test
    void garbage_bearer_sets_invalid_token_marker() throws Exception {
        assertThat(run("Bearer abc").getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE))
                .isEqualTo(Boolean.TRUE);
    }

    @Test
    void refresh_blacklisted_and_other_church_tokens_set_invalid_token_marker() throws Exception {
        assertThat(run("Bearer " + provider.issueRefresh("uuid-7"))
                        .getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE))
                .as("refresh")
                .isEqualTo(Boolean.TRUE);

        String blocked = access();
        blacklist.block(provider.parse(blocked).getId());
        assertThat(run("Bearer " + blocked).getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE))
                .as("blacklisted")
                .isEqualTo(Boolean.TRUE);

        String otherChurch = TenantContext.callAs(999L, this::access);
        assertThat(run("Bearer " + otherChurch).getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE))
                .as("other church")
                .isEqualTo(Boolean.TRUE);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void valid_bearer_sets_authentication_and_no_marker() throws Exception {
        MockHttpServletRequest request = run("Bearer " + access());
        assertThat(request.getAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE))
                .isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
