package com.elipair.church.domain.auth;

import com.elipair.church.domain.auth.dto.SignupRequest;
import com.elipair.church.domain.auth.dto.SignupResponse;
import com.elipair.church.domain.member.Member;
import com.elipair.church.domain.member.MemberRepository;
import com.elipair.church.domain.member.PhoneNumbers;
import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import com.elipair.church.global.exception.BusinessException;
import com.elipair.church.global.exception.ErrorCode;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.redis.RefreshTokenStore;
import com.elipair.church.global.security.redis.TokenBlacklist;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final MemberRepository memberRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklist tokenBlacklist;

    public AuthService(
            MemberRepository memberRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            RefreshTokenStore refreshTokenStore,
            TokenBlacklist tokenBlacklist) {
        this.memberRepository = memberRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String phone = PhoneNumbers.normalize(request.phone());
        if (memberRepository.existsByPhoneAndDeletedAtIsNull(phone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
        }
        Member member = Member.create(
                phone,
                request.name(),
                passwordEncoder.encode(request.password()),
                request.email(),
                null,
                request.termsAgreed(),
                request.privacyAgreed());
        Role userRole = roleRepository
                .findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("USER 역할 시드(V2)가 없습니다"));
        member.grantRole(userRole);
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE); // 동시 가입 경합 백스톱(partial unique)
        }
        return SignupResponse.from(member);
    }
}
