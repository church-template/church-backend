package com.elipair.church.domain.member.controller;

import com.elipair.church.domain.member.MemberRoleService;
import com.elipair.church.domain.member.MemberService;
import com.elipair.church.domain.member.dto.AdminMemberUpdateRequest;
import com.elipair.church.domain.member.dto.MemberDetailResponse;
import com.elipair.church.domain.member.dto.ResetPasswordResponse;
import com.elipair.church.domain.member.dto.RoleGrantRequest;
import com.elipair.church.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "회원(관리)")
@RestController
@RequestMapping("/api/admin/members")
public class MemberAdminController {

    private final MemberService memberService;
    private final MemberRoleService memberRoleService;

    public MemberAdminController(MemberService memberService, MemberRoleService memberRoleService) {
        this.memberService = memberService;
        this.memberRoleService = memberRoleService;
    }

    @Operation(
            summary = "회원 정보 수정",
            description = "MEMBER_MANAGE 필요. 관리자가 대상 회원의 이름·직분 등을 수정. soft delete 회원에는 적용 안 됨.")
    @PatchMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    public MemberDetailResponse update(@PathVariable UUID uuid, @Valid @RequestBody AdminMemberUpdateRequest request) {
        return memberService.adminUpdate(uuid, request);
    }

    @Operation(summary = "비밀번호 초기화", description = "MEMBER_MANAGE 필요. 임시 비밀번호 생성 후 반환. 대상 회원은 다음 로그인 시 변경 권고.")
    @PostMapping("/{uuid}/reset-password")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    public ResetPasswordResponse resetPassword(@PathVariable UUID uuid) {
        return memberService.resetPassword(uuid);
    }

    @Operation(
            summary = "역할 부여",
            description = "ROLE_MANAGE 필요. 요청자 maxPriority 이하 역할만 부여 가능(에스컬레이션 방지). 자기 자신 부여 불가. is_system 역할 수정 불가.")
    @PostMapping("/{uuid}/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public MemberDetailResponse grantRole(
            @PathVariable UUID uuid,
            @Valid @RequestBody RoleGrantRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return memberRoleService.grant(uuid, request.roleId(), principal.id(), principal.maxPriority());
    }

    @Operation(summary = "역할 회수", description = "ROLE_MANAGE 필요. 요청자 maxPriority 이하 역할만 회수 가능. 마지막 SUPER_ADMIN 회수 불가.")
    @DeleteMapping("/{uuid}/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeRole(
            @PathVariable UUID uuid, @PathVariable Long roleId, @AuthenticationPrincipal MemberPrincipal principal) {
        memberRoleService.revoke(uuid, roleId, principal.id(), principal.maxPriority());
    }
}
