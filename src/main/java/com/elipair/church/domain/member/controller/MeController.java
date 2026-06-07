package com.elipair.church.domain.member.controller;

import com.elipair.church.domain.member.MemberService;
import com.elipair.church.domain.member.dto.AgreementResponse;
import com.elipair.church.domain.member.dto.AgreementUpdateRequest;
import com.elipair.church.domain.member.dto.MeResponse;
import com.elipair.church.domain.member.dto.MeUpdateRequest;
import com.elipair.church.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 본인 회원 정보·약관(스펙 §5.2). path는 공개지만 메서드 보안으로 인증 강제(익명→401). */
@Tag(name = "내 정보", description = "본인 회원 정보·약관 조회/수정 API(스펙 §5.2). 전 엔드포인트 인증 필요.")
@RestController
@RequestMapping("/api/members/me")
@PreAuthorize("isAuthenticated()")
public class MeController {

    private final MemberService service;

    public MeController(MemberService service) {
        this.service = service;
    }

    @Operation(summary = "내 정보 조회", description = "인증 필요. JWT에 없는 최신 정보(이름·전화번호·역할 등)를 DB에서 반환.")
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal MemberPrincipal principal) {
        return service.getMe(principal.id());
    }

    @Operation(summary = "내 정보 수정", description = "인증 필요. 이름·전화번호·비밀번호 변경 가능. 변경 후 다음 refresh 시 JWT에 반영.")
    @PatchMapping
    public MeResponse updateMe(
            @AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody MeUpdateRequest request) {
        return service.updateMe(principal.id(), request);
    }

    @Operation(summary = "내 동의 조회", description = "인증 필요. 현재 약관·개인정보 동의 상태(agreed_at 포함) 반환.")
    @GetMapping("/agreements")
    public AgreementResponse agreements(@AuthenticationPrincipal MemberPrincipal principal) {
        return service.getAgreements(principal.id());
    }

    @Operation(
            summary = "재동의 제출",
            description = "인증 필요. termsAgreed·privacyAgreed 모두 true 필수. agreed_at 갱신 + requiresAgreement 플래그 해제.")
    @PatchMapping("/agreements")
    public AgreementResponse submitAgreements(
            @AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody AgreementUpdateRequest request) {
        return service.submitAgreements(principal.id(), request);
    }
}
