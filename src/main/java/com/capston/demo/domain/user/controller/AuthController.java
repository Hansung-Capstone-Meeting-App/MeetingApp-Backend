package com.capston.demo.domain.user.controller;

import com.capston.demo.domain.user.dto.request.LoginRequestDto;
import com.capston.demo.domain.user.dto.response.LoginResponseDto;
import com.capston.demo.domain.user.dto.request.LogoutRequestDto;
import com.capston.demo.domain.user.dto.request.RefreshTokenRequestDto;
import com.capston.demo.domain.user.dto.response.RefreshTokenResponseDto;
import com.capston.demo.domain.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 로그인 API: 이메일/비밀번호로 로그인하여 Access Token 및 Refresh Token 반환
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto) {
        LoginResponseDto response = authService.login(requestDto);
        return ResponseEntity.ok(response);
    }

    // 토큰 갱신 API: Refresh Token으로 새로운 Access Token 발급
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto requestDto) {
        RefreshTokenResponseDto response = authService.refreshAccessToken(requestDto.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    // 로그아웃 API: DB에서 Refresh Token 삭제
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequestDto requestDto) {
        authService.logout(requestDto.getUserId());
        return ResponseEntity.ok().build();
    }
}
