package com.elipair.church.domain.member;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.elipair.church.domain.role.Role;
import com.elipair.church.domain.role.RoleRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SuperAdminInitializerTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private SuperAdminInitializer initializer(String phone, String name, String password) {
        return new SuperAdminInitializer(
                memberRepository, roleRepository, passwordEncoder, new AdminBootstrapProperties(phone, name, password));
    }

    @Test
    void creates_super_admin_when_none_active_and_env_present() throws Exception {
        when(memberRepository.existsByRoles_NameAndDeletedAtIsNull("SUPER_ADMIN"))
                .thenReturn(false);
        when(roleRepository.findByName("SUPER_ADMIN"))
                .thenReturn(Optional.of(Role.create("SUPER_ADMIN", 1000, "최고관리자")));
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}");

        initializer("010-1234-5678", "관리자", "secret123").run(null);

        verify(memberRepository).save(any(Member.class));
    }

    @Test
    void skips_when_active_super_admin_exists() throws Exception {
        when(memberRepository.existsByRoles_NameAndDeletedAtIsNull("SUPER_ADMIN"))
                .thenReturn(true);

        initializer("010-1234-5678", "관리자", "secret123").run(null);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void skips_when_env_missing() throws Exception {
        initializer(null, null, null).run(null);

        verify(memberRepository, never()).existsByRoles_NameAndDeletedAtIsNull(anyString());
        verify(memberRepository, never()).save(any());
    }
}
