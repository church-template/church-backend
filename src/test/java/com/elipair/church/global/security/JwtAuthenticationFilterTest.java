package com.elipair.church.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.elipair.church.global.security.redis.TokenBlacklist;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "filter-test-secret-filter-test-secret-0123";
    private final JwtTokenProvider provider = new JwtTokenProvider(new JwtProperties(SECRET, 3600, 1209600));
    private final TokenBlacklistStub blacklist = new TokenBlacklistStub();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider, blacklist);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest withToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    @Test
    void valid_access_token_populates_context() throws Exception {
        String token =
                provider.issueAccess(new MemberPrincipal(7L, "uuid-7", "홍길동", 900), "장로", List.of("SERMON_WRITE"));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(withToken(token), new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(MemberPrincipal.class);
        assertThat(((MemberPrincipal) auth.getPrincipal()).id()).isEqualTo(7L);
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("SERMON_WRITE"));
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blacklisted_token_leaves_context_empty() throws Exception {
        String token = provider.issueAccess(new MemberPrincipal(7L, "uuid-7", "n", 0), null, List.of());
        blacklist.block(provider.parse(token).getId());

        filter.doFilter(withToken(token), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void refresh_token_used_as_access_is_rejected() throws Exception {
        String refresh = provider.issueRefresh("uuid-7");

        filter.doFilter(withToken(refresh), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void malformed_token_does_not_throw_and_leaves_context_empty() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(withToken("not-a-jwt"), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void no_header_leaves_context_empty() throws Exception {
        filter.doFilter(withToken(null), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expired_token_leaves_context_empty() throws Exception {
        JwtTokenProvider expiring = new JwtTokenProvider(new JwtProperties(SECRET, -60, 1209600));
        String token = expiring.issueAccess(new MemberPrincipal(7L, "uuid-7", "n", 0), null, List.of());

        filter.doFilter(withToken(token), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** isBlacklisted만 제어하는 경량 스텁(실제 Redis 불필요). */
    static class TokenBlacklistStub extends TokenBlacklist {
        private final java.util.Set<String> blocked = new java.util.HashSet<>();

        TokenBlacklistStub() {
            super(null);
        }

        void block(String jti) {
            blocked.add(jti);
        }

        @Override
        public boolean isBlacklisted(String jti) {
            return blocked.contains(jti);
        }
    }
}
