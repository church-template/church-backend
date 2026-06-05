package com.elipair.church.domain.member;

import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 기동 시 최초 SUPER_ADMIN 계정을 멱등 생성한다(스펙 §3.3).
 * 활성 SUPER_ADMIN이 없고 admin.bootstrap.* env가 모두 채워졌을 때만 1명 생성. members+roles 참조라 domain에 둔다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class SuperAdminInitializer implements ApplicationRunner {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final MemberRepository memberRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    public SuperAdminInitializer(
            MemberRepository memberRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AdminBootstrapProperties properties) {
        this.memberRepository = memberRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isComplete()) {
            log.warn("admin.bootstrap.* 미설정 — SUPER_ADMIN 부트스트랩을 건너뜁니다(ADMIN_PHONE/NAME/PASSWORD 확인).");
            return;
        }
        if (memberRepository.existsByRoles_NameAndDeletedAtIsNull(SUPER_ADMIN)) {
            return; // 멱등: 이미 활성 SUPER_ADMIN 존재
        }
        Role superAdmin = roleRepository
                .findByName(SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN 역할 시드(V2)가 없습니다"));
        Member admin = Member.create(
                PhoneNumbers.normalize(properties.phone()),
                properties.name(),
                passwordEncoder.encode(properties.password()),
                null,
                null,
                true,
                true);
        admin.grantRole(superAdmin);
        memberRepository.save(admin);
        log.info("최초 SUPER_ADMIN 계정을 생성했습니다(phone={}).", admin.getPhone());
    }
}
