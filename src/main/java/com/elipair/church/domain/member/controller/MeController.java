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

    @Operation(summary = "내 정보 조회", description = """
                    JWT에 담기지 않은 최신 본인 정보를 DB에서 조회. 토큰값이 낡았을 때 라이브 값을 얻는 용도.

                    - 인증(JWT): 필요 — 로그인(본인)
                    - 반환값: `MeResponse` — uuid·이름·전화번호·이메일·직분·역할·권한·maxPriority·약관 동의 상태(agreedAt 포함)
                    """)
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal MemberPrincipal principal) {
        return service.getMe(principal.id());
    }

    @Operation(summary = "내 정보 수정", description = """
                    본인 프로필 부분 수정(PATCH). 제공한 필드만 변경(null=미변경). 변경값은 다음 refresh 시 JWT에 반영.

                    - 인증(JWT): 필요 — 로그인(본인)
                    - 요청 본문: `MeUpdateRequest` — 이름·전화번호·비밀번호(있으면 8자 이상)·이메일 (모두 선택)
                    - 반환값: `MeResponse` — 수정된 본인 정보
                    - 부수효과: 전화번호 변경 시 자기 제외 중복이면 409 DUPLICATE_RESOURCE
                    """)
    @PatchMapping
    public MeResponse updateMe(
            @AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody MeUpdateRequest request) {
        return service.updateMe(principal.id(), request);
    }

    @Operation(summary = "내 동의 조회", description = """
                    본인의 현재 약관·개인정보 동의 상태를 조회.

                    - 인증(JWT): 필요 — 로그인(본인)
                    - 반환값: `AgreementResponse` — `termsAgreed`·`privacyAgreed`·`agreedAt`
                    """)
    @GetMapping("/agreements")
    public AgreementResponse agreements(@AuthenticationPrincipal MemberPrincipal principal) {
        return service.getAgreements(principal.id());
    }

    @Operation(summary = "재동의 제출", description = """
                    약관 리셋 후 재동의 제출(PATCH). 두 동의가 모두 true여야 성립한다.

                    - 인증(JWT): 필요 — 로그인(본인)
                    - 요청 본문: `AgreementUpdateRequest` — `termsAgreed`·`privacyAgreed`(둘 다 true 필수)
                    - 반환값: `AgreementResponse` — 갱신된 동의 상태(agreedAt 포함)
                    - 부수효과: agreedAt 갱신 + 동의 플래그 set → 이후 로그인 시 requiresAgreement=false. 둘 중 하나라도 false면 400 INVALID_INPUT_VALUE
                    """)
    @PatchMapping("/agreements")
    public AgreementResponse submitAgreements(
            @AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody AgreementUpdateRequest request) {
        return service.submitAgreements(principal.id(), request);
    }
}
