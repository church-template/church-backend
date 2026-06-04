package com.elipair.church.global.security;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * BaseEntity의 created_by/updated_by를 채우는 감사자 공급원(스펙 §6, BaseEntity 주석 "#4부터 채움").
 * SecurityContext의 MemberPrincipal.id(=member.id)를 반환 — DB 조회 없음. 미인증이면 Optional.empty().
 * JpaConfig#securityAuditorAware()로 빈 등록 — 직접 @Component 불필요.
 */
public class SecurityAuditorAware implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.ofNullable(principal.id());
    }
}
