package com.elipair.church.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.auth.dto.LoginRequest;
import com.elipair.church.domain.auth.dto.SignupRequest;
import com.elipair.church.domain.auth.dto.SignupResponse;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.redis.RefreshTokenStore;
import com.elipair.church.global.security.redis.TokenBlacklist;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private TokenBlacklist tokenBlacklist;

    @InjectMocks private AuthService authService;

    @Test
    void signup_normalizes_phone_grants_user_and_returns_summary() {
        when(memberRepository.existsByPhoneAndDeletedAtIsNull("01012345678")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("{bcrypt}");
        Role userRole = mock(Role.class);
        when(userRole.getName()).thenReturn("USER");
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(userRole));
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        SignupResponse res = authService.signup(
                new SignupRequest("010-1234-5678", "홍길동", "password123", null, true, true));

        assertThat(res.name()).isEqualTo("홍길동");
        assertThat(res.phone()).isEqualTo("01012345678");
        assertThat(res.roles()).containsExactly("USER");
    }

    @Test
    void signup_duplicate_phone_is_duplicate_resource() {
        when(memberRepository.existsByPhoneAndDeletedAtIsNull("01012345678")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(
                        new SignupRequest("010-1234-5678", "홍길동", "password123", null, true, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void signup_rejects_when_consent_not_given() {
        assertThatThrownBy(() -> authService.signup(
                        new SignupRequest("010-1234-5678", "홍길동", "password123", null, false, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void login_unknown_phone_is_authentication_failed() {
        when(memberRepository.findByPhoneAndDeletedAtIsNull("01012345678")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("010-1234-5678", "pw")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTHENTICATION_FAILED);
    }

    @Test
    void login_wrong_password_is_authentication_failed() {
        Member m = Member.create("01012345678", "홍길동", "{enc}", null, null, true, true);
        when(memberRepository.findByPhoneAndDeletedAtIsNull("01012345678")).thenReturn(Optional.of(m));
        when(passwordEncoder.matches(eq("pw"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("010-1234-5678", "pw")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTHENTICATION_FAILED); // 미존재와 동일 코드 — 열거 방지
    }
}
