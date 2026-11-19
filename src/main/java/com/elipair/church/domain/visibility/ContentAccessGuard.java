package com.elipair.church.domain.visibility;

import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.security.JwtAuthenticationFilter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 5개 메뉴 읽기 경로의 접근 판단(#95). "누가 보는가"는 컨트롤러에서만 판단한다 — 서비스가 로그인 상태를 읽으면
 * 보는 사람과 무관한 캐시(/api/main)에 교인용 결과가 섞인다(설계 6.6-1).
 * 메뉴 설정은 교회마다 DB에 있어 고정 경로 매처로 표현할 수 없으므로 SecurityConfig가 아니라 여기서 본다.
 */
@Component
public class ContentAccessGuard {

    private final VisibilityService visibility;

    public ContentAccessGuard(VisibilityService visibility) {
        this.visibility = visibility;
    }

    /** 이 요청이 해당 도메인의 조회 권한을 가졌는가. */
    public boolean isMember(ContentDomain domain) {
        return hasAuthority(domain.viewPermission());
    }

    /** 메뉴가 MEMBERS인데 권한이 없으면 401(비로그인)/403(권한 없음). 무효 토큰이 왔으면 메뉴가 PUBLIC이어도 401. */
    public void requireMenu(ContentDomain domain) {
        rejectInvalidToken();
        if (!visibility.isPublic(domain)) {
            requireView(domain);
        }
    }

    /** membersOnly == true 인 글을 권한 없이 보려 하면 401/403. */
    public void requireItem(ContentDomain domain, boolean membersOnly) {
        rejectInvalidToken();
        if (membersOnly) {
            requireView(domain);
        }
    }

    /** 쓰기 권한(예: "SERMON_WRITE") 보유 여부 — 관리자용 필터 허용 판단. */
    public boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private void requireView(ContentDomain domain) {
        if (isMember(domain)) {
            return;
        }
        // 비로그인은 401(로그인하면 볼 수 있다는 안내), 로그인했는데 권한이 없으면 403(승인 후 이용) — 기존 경로 규칙과 같다.
        throw new BusinessException(loggedIn() ? ErrorCode.ACCESS_DENIED : ErrorCode.INVALID_TOKEN);
    }

    private static boolean loggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    // 만료된 토큰을 든 교인이 교인 전용 글이 빠진 목록을 오류 없이 받지 않게 한다 — 401을 받아야 프론트가 토큰을 갱신한다.
    private static void rejectInvalidToken() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null
                && Boolean.TRUE.equals(attributes.getAttribute(
                        JwtAuthenticationFilter.INVALID_TOKEN_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST))) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }
}
