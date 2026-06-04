package com.elipair.church.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 부트스트랩용 최소 보안 셸(이슈 #2). spring-security 기본 default-deny가 Swagger·헬스체크까지
 * 막으므로, 앱 기동·문서 노출·compose 헬스체크에 필요한 인프라 경로만 permit 하고 나머지는
 * authenticated 로 둔다.
 *
 * <p>경로 3분법(/api/admin/** 쓰기권한, /api/gallery/** GALLERY_VIEW, 그 외 /api/** 공개),
 * JWT 인증 필터, Swagger 경로의 RBAC 게이팅(운영 시 permitAll → 권한 필요)은 #4(보안 기반)에서
 * 이 셸을 확장해 구현한다. 도메인 엔드포인트가 아직 없어 여기선 인프라 경로만 다룬다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Swagger / OpenAPI — publicly accessible (toggled via SWAGGER_ENABLED)
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // Error dispatch — must be accessible to surface 404/5xx responses
                        .requestMatchers("/error")
                        .permitAll()
                        // Actuator health — docker-compose healthcheck requires unauthenticated access
                        .requestMatchers("/actuator/health")
                        .permitAll()
                        .anyRequest()
                        .authenticated());
        return http.build();
    }
}
