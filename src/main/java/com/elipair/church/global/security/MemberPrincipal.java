package com.elipair.church.global.security;

/**
 * 인증된 회원의 SecurityContext principal. uuid는 외부 식별자, id(member.id)는 내부 감사용(mid 클레임).
 * 권한은 principal이 아니라 Authentication의 authorities가 보유한다.
 */
public record MemberPrincipal(Long id, String uuid, String name, int maxPriority) {}
