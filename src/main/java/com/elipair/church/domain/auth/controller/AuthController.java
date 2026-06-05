package com.elipair.church.domain.auth.controller;

import com.elipair.church.domain.auth.AuthService;
import com.elipair.church.domain.auth.dto.LoginRequest;
import com.elipair.church.domain.auth.dto.LoginResponse;
import com.elipair.church.domain.auth.dto.RefreshRequest;
import com.elipair.church.domain.auth.dto.RefreshResponse;
import com.elipair.church.domain.auth.dto.SignupRequest;
import com.elipair.church.domain.auth.dto.SignupResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증(스펙 §5.1). signup·login·refresh는 공개, logout만 메서드 보안으로 인증 강제. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }
}
