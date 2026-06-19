package com.elipair.church.domain.member;

import com.elipair.church.domain.role.Permission;
import com.elipair.church.domain.role.Role;
import java.util.List;

/** 회원의 역할 집합을 펼친 권한 목록·maxPriority로 변환(스펙 §4.2). /me 응답·D4 토큰 발급이 공유. */
public final class MemberAuthorities {

    private MemberAuthorities() {}

    public static List<String> permissions(Member member) {
        return member.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .distinct()
                .sorted()
                .toList();
    }

    public static int maxPriority(Member member) {
        return member.getRoles().stream().mapToInt(Role::getPriority).max().orElse(0);
    }

    /**
     * 회원 전용 콘텐츠 열람 권한(GALLERY_VIEW) 보유 = 승인 상태.
     * MEMBER(교인)뿐 아니라 전 권한을 가진 ADMIN·SUPER_ADMIN도 승인으로 본다(권한 기준, 역할명 비의존).
     */
    public static boolean isApproved(Member member) {
        return member.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(p -> GALLERY_VIEW.equals(p.getName()));
    }

    private static final String GALLERY_VIEW = "GALLERY_VIEW";
}
