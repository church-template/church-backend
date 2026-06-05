package com.elipair.church.domain.member.controller;

import com.elipair.church.domain.member.MemberRoleService;
import com.elipair.church.domain.member.MemberService;
import com.elipair.church.domain.member.dto.AdminMemberUpdateRequest;
import com.elipair.church.domain.member.dto.MemberDetailResponse;
import com.elipair.church.domain.member.dto.ResetPasswordResponse;
import com.elipair.church.domain.member.dto.RoleGrantRequest;
import com.elipair.church.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 회원 관리(스펙 §5.2). /api/admin/** 인증 + 메서드별 권한. */
@RestController
@RequestMapping("/api/admin/members")
public class MemberAdminController {

    private final MemberService memberService;
    private final MemberRoleService memberRoleService;

    public MemberAdminController(MemberService memberService, MemberRoleService memberRoleService) {
        this.memberService = memberService;
        this.memberRoleService = memberRoleService;
    }

    @PatchMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    public MemberDetailResponse update(@PathVariable UUID uuid, @Valid @RequestBody AdminMemberUpdateRequest request) {
        return memberService.adminUpdate(uuid, request);
    }

    @PostMapping("/{uuid}/reset-password")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    public ResetPasswordResponse resetPassword(@PathVariable UUID uuid) {
        return memberService.resetPassword(uuid);
    }

    @PostMapping("/{uuid}/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public MemberDetailResponse grantRole(
            @PathVariable UUID uuid,
            @Valid @RequestBody RoleGrantRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return memberRoleService.grant(uuid, request.roleId(), principal.id(), principal.maxPriority());
    }

    @DeleteMapping("/{uuid}/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeRole(
            @PathVariable UUID uuid, @PathVariable Long roleId, @AuthenticationPrincipal MemberPrincipal principal) {
        memberRoleService.revoke(uuid, roleId, principal.id(), principal.maxPriority());
    }
}
