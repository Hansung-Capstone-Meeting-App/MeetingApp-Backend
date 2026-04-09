package com.capston.demo.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정 (모바일 앱용 REST API)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF 비활성화 (JWT 사용 시 불필요, 모바일 앱에서는 CSRF 공격 불가)
        http.csrf((csrf) -> csrf.disable());

        // 세션 관리 stateless로 설정 (JWT 사용, 서버에 세션 저장 안 함)
        http.sessionManagement((session) ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        // 요청 권한 설정
        http.authorizeHttpRequests((authorize) ->
                authorize
                        // Swagger UI (공개)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // 인증 관련 API (공개)
                        .requestMatchers("/api/auth/**").permitAll()
                        // 사용자 등록 API (공개)
                        .requestMatchers("/api/user/register", "/api/user/presigned-url").permitAll()
                        // OAuth2 API (공개)
                        .requestMatchers("/api/oauth2/**").permitAll()
                        // Slack 이벤트 (공개 — Slack 서버에서 호출)
                        .requestMatchers("/slack/events").permitAll()
                        // 나머지는 인증 필요
                        .anyRequest().authenticated()
        );

        // JWT 필터 추가 (모든 요청에 대해 JWT 검증)
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // 폼 로그인 비활성화 (JWT 사용, 모바일 앱에서는 폼 로그인 불필요)
        http.formLogin((formLogin) -> formLogin.disable());

        return http.build();
    }
}