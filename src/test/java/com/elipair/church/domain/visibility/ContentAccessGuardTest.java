package com.elipair.church.domain.visibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.security.JwtAuthenticationFilter;
import com.elipair.church.global.security.MemberPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ContentAccessGuardTest {

    private final VisibilityService visibility = mock(VisibilityService.class);
    private final ContentAccessGuard guard = new ContentAccessGuard(visibility);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    void setUp() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(visibility.isPublic(ContentDomain.NOTICE)).thenReturn(true);
        when(visibility.isPublic(ContentDomain.SERMON)).thenReturn(false);
        when(visibility.isPublic(ContentDomain.GALLERY)).thenReturn(false);
        anonymous();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    /** 실제 체인처럼 비로그인 요청에는 AnonymousAuthenticationFilter가 익명 인증을 세운다. */
    private void anonymous() {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    }

    private void loggedIn(String... permissions) {
        List<SimpleGrantedAuthority> authorities =
                List.of(permissions).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        new MemberPrincipal(1L, "u", "n", 100), null, authorities));
    }

    private static void assertError(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(code);
    }

    @Test
    void public_menu_passes_for_anonymous() {
        assertThatCode(() -> guard.requireMenu(ContentDomain.NOTICE)).doesNotThrowAnyException();
    }

    @Test
    void members_menu_anonymous_is_401_invalid_token() {
        assertError(() -> guard.requireMenu(ContentDomain.SERMON), ErrorCode.INVALID_TOKEN);
    }

    @Test
    void members_menu_without_view_permission_is_403_access_denied() {
        loggedIn("NOTICE_WRITE");
        assertError(() -> guard.requireMenu(ContentDomain.SERMON), ErrorCode.ACCESS_DENIED);
    }

    @Test
    void members_menu_with_view_permission_passes() {
        loggedIn("SERMON_VIEW");
        assertThatCode(() -> guard.requireMenu(ContentDomain.SERMON)).doesNotThrowAnyException();
    }

    @Test
    void public_menu_with_invalid_token_marker_is_401() {
        request.setAttribute(JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
        assertError(() -> guard.requireMenu(ContentDomain.NOTICE), ErrorCode.INVALID_TOKEN);
    }

    @Test
    void members_only_item_anonymous_is_401_and_plain_item_passes() {
        assertError(() -> guard.requireItem(ContentDomain.SERMON, true), ErrorCode.INVALID_TOKEN);
        assertThatCode(() -> guard.requireItem(ContentDomain.SERMON, false)).doesNotThrowAnyException();
    }

    @Test
    void members_only_item_without_view_permission_is_403_and_with_it_passes() {
        loggedIn("NOTICE_WRITE");
        assertError(() -> guard.requireItem(ContentDomain.SERMON, true), ErrorCode.ACCESS_DENIED);
        loggedIn("SERMON_VIEW");
        assertThatCode(() -> guard.requireItem(ContentDomain.SERMON, true)).doesNotThrowAnyException();
    }

    @Test
    void is_member_is_false_for_other_domains_permission() {
        loggedIn("SERMON_VIEW");
        assertThat(guard.isMember(ContentDomain.SERMON)).isTrue();
        assertThat(guard.isMember(ContentDomain.GALLERY)).isFalse();
        assertThat(guard.hasAuthority("SERMON_VIEW")).isTrue();
        assertThat(guard.hasAuthority("SERMON_WRITE")).isFalse();
    }
}
