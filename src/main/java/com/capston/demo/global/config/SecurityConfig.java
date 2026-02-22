package com.capston.demo.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf((csrf) -> csrf.disable()); // csrf 보안 기능 끄기
        http.authorizeHttpRequests((authorize) ->
                authorize.requestMatchers("/**").permitAll()
        ); //모든 url에 로그인 검사 해제
        // 폼으로 로그인
        http.formLogin((formLogin)
                        -> formLogin.loginPage("/login")
                        .defaultSuccessUrl("/")
        );
        //로그아웃
        http.logout( logout -> logout.logoutUrl("/logout") );
        return http.build();
    }
}