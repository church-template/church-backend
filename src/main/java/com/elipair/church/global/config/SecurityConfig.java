package com.elipair.church.global.config;

import com.elipair.church.global.security.JwtAccessDeniedHandler;
import com.elipair.church.global.security.JwtAuthenticationEntryPoint;
import com.elipair.church.global.security.JwtAuthenticationFilter;
import com.elipair.church.global.security.JwtProperties;
import com.elipair.church.global.security.JwtTokenProvider;
import com.elipair.church.global.security.redis.TokenBlacklist;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 경로 3분법(스펙 §4.3): /api/admin/** 인증(세부 권한은 메서드 @PreAuthorize), /api/gallery/** GALLERY_VIEW,
 * /api/bible-challenges/** CHALLENGE_PARTICIPATE, 그 외 공개. JWT 필터·인증 실패 RFC 7807 변환·CORS·메서드 보안을 배선한다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklist tokenBlacklist;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final String corsAllowedOrigin;

    public SecurityConfig(
            JwtTokenProvider tokenProvider,
            TokenBlacklist tokenBlacklist,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler,
            @Value("${cors.allowed-origin}") String corsAllowedOrigin) {
        this.tokenProvider = tokenProvider;
        this.tokenBlacklist = tokenBlacklist;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsAllowedOrigin = corsAllowedOrigin;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/v3/api-docs", "/v3/api-docs/**", "/docs/swagger-ui/**", "/docs/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/actuator/health")
                        .permitAll()
                        .requestMatchers("/api/admin/**")
                        .authenticated()
                        .requestMatchers("/api/gallery/**")
                        .hasAuthority("GALLERY_VIEW")
                        .requestMatchers("/api/bible-challenges/**")
                        .hasAuthority("CHALLENGE_PARTICIPATE")
                        .anyRequest()
                        .permitAll())
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(
                        new JwtAuthenticationFilter(tokenProvider, tokenBlacklist),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 회원 비밀번호 해시(스펙 §4.1, 복잡도 강제 없음·BCrypt). reset-password·비번변경·부트스트랩·D4 login이 사용. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        // allowCredentials(true)는 와일드카드 origin과 공존할 수 없다(브라우저·Spring 모두 거부).
        // 운영자가 CORS_ALLOWED_ORIGIN=*로 잘못 넣으면 모호한 런타임 오류 대신 기동 시 명확히 실패시킨다.
        if ("*".equals(corsAllowedOrigin)) {
            throw new IllegalStateException(
                    "cors.allowed-origin은 '*'일 수 없습니다(credentialed CORS). CORS_ALLOWED_ORIGIN에 교회 프론트 도메인을 지정하세요.");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(corsAllowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
