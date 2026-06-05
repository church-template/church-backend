package com.elipair.church.domain.member.controller;

import com.elipair.church.domain.member.MemberService;
import com.elipair.church.domain.member.dto.AgreementResponse;
import com.elipair.church.domain.member.dto.AgreementUpdateRequest;
import com.elipair.church.domain.member.dto.MeResponse;
import com.elipair.church.domain.member.dto.MeUpdateRequest;
import com.elipair.church.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 본인 회원 정보·약관(스펙 §5.2). path는 공개지만 메서드 보안으로 인증 강제(익명→401). */
@RestController
@RequestMapping("/api/members/me")
@PreAuthorize("isAuthenticated()")
public class MeController {

    private final MemberService service;

    public MeController(MemberService service) {
        this.service = service;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal MemberPrincipal principal) {
        return service.getMe(principal.id());
    }

    @PatchMapping
    public MeResponse updateMe(
            @AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody MeUpdateRequest request) {
        return service.updateMe(principal.id(), request);
    }

    @GetMapping("/agreements")
    public AgreementResponse agreements(@AuthenticationPrincipal MemberPrincipal principal) {
        return service.getAgreements(principal.id());
    }

    @PatchMapping("/agreements")
    public AgreementResponse submitAgreements(
            @AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody AgreementUpdateRequest request) {
        return service.submitAgreements(principal.id(), request);
    }
}
