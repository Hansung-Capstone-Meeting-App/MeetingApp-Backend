package com.capston.demo.domain.user.controller;


import com.capston.demo.domain.user.dto.request.LoginRequestDto;
import com.capston.demo.domain.user.dto.request.LogoutRequestDto;
import com.capston.demo.domain.user.dto.request.RefreshTokenRequestDto;
import com.capston.demo.domain.user.dto.response.LoginResponseDto;
import com.capston.demo.domain.user.dto.response.RefreshTokenResponseDto;
import com.capston.demo.domain.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 컨트롤러 (모바일 앱용 REST API)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 로그인 API
     * 이메일/비밀번호로 로그인하여 JWT 토큰 반환
     *
     * @param requestDto 로그인 정보 (이메일, 비밀번호)
     * @return JWT 액세스 토큰 및 리프레시 토큰
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto) {
        LoginResponseDto response = authService.login(requestDto);
        return ResponseEntity.ok(response);
    }

    /**
     * 토큰 갱신 API
     * Refresh Token으로 새로운 Access Token 발급
     *
     * @param requestDto Refresh Token
     * @return 새로운 Access Token
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto requestDto) {
        RefreshTokenResponseDto response = authService.refreshAccessToken(requestDto.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * 로그아웃 API
     * DB에서 Refresh Token 삭제
     *
     * @param requestDto 사용자 ID
     * @return 성공 응답
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequestDto requestDto) {
        authService.logout(requestDto.getUserId());
        return ResponseEntity.ok().build();
    }
}
