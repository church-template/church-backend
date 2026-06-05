package com.elipair.church.domain.member.controller;

import com.elipair.church.domain.member.MemberService;
import com.elipair.church.domain.member.dto.AgreementResetRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 약관 재동의 사이클 — 관리자 일괄 리셋(스펙 §5.2 방식 A). */
@RestController
@RequestMapping("/api/admin/agreements")
public class AgreementAdminController {

    private final MemberService memberService;

    public AgreementAdminController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/reset")
    @PreAuthorize("hasAuthority('MEMBER_MANAGE')")
    @ResponseStatus(HttpStatus.OK)
    public void reset(@Valid @RequestBody AgreementResetRequest request) {
        memberService.resetAgreements(request.target());
    }
}
