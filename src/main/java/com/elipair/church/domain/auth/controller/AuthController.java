package com.elipair.church.domain.auth.controller;

import com.elipair.church.domain.auth.AuthService;
import com.elipair.church.domain.auth.dto.LoginRequest;
import com.elipair.church.domain.auth.dto.LoginResponse;
import com.elipair.church.domain.auth.dto.LogoutRequest;
import com.elipair.church.domain.auth.dto.RefreshRequest;
import com.elipair.church.domain.auth.dto.RefreshResponse;
import com.elipair.church.domain.auth.dto.SignupRequest;
import com.elipair.church.domain.auth.dto.SignupResponse;
import com.elipair.church.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증(스펙 §5.1). signup·login·refresh는 공개, logout만 메서드 보안으로 인증 강제. */
@Tag(name = "인증", description = "회원가입·로그인·토큰 재발급·로그아웃 API(스펙 §5.1)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
            summary = "회원가입",
            description = "공개. termsAgreed·privacyAgreed 모두 true 필수. 중복 전화번호 409 DUPLICATE_RESOURCE.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @Operation(
            summary = "로그인",
            description =
                    "공개. 전화번호+비밀번호 인증. 성공 시 accessToken·refreshToken·member 반환. requiresAgreement=true면 재동의 필요. 실패 시 phone 존재 여부 미노출(401 AUTHENTICATION_FAILED).")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(
            summary = "토큰 재발급",
            description = "공개. refreshToken으로 새 accessToken·refreshToken 발급. 토큰 무효·만료 시 401 INVALID_TOKEN.")
    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @Operation(summary = "로그아웃", description = "인증 필요. accessToken Redis 블랙리스트 등록 + refreshToken 무효화. 이후 해당 토큰 재사용 불가.")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void logout(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody LogoutRequest request) {
        String accessToken = (authorization != null && authorization.startsWith("Bearer "))
                ? authorization.substring(7)
                : authorization;
        authService.logout(principal, accessToken, request.refreshToken());
    }
}
