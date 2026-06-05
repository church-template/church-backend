package com.elipair.church.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank String phone,
        @NotBlank String name,
        @NotBlank @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다") String password,
        @Email String email,
        @AssertTrue(message = "이용약관에 동의해야 합니다") boolean termsAgreed,
        @AssertTrue(message = "개인정보 수집·이용에 동의해야 합니다") boolean privacyAgreed) {}
