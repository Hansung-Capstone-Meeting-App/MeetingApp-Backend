package com.capston.demo.domain.user.service;


import com.capston.demo.domain.user.dto.request.LoginRequestDto;
import com.capston.demo.domain.user.dto.response.LoginResponseDto;
import com.capston.demo.domain.user.dto.response.RefreshTokenResponseDto;
import com.capston.demo.domain.user.entity.RefreshToken;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.repository.RefreshTokenRepository;
import com.capston.demo.domain.user.repository.UserRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import com.capston.demo.global.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // 일반 로그인 처리: 이메일/비밀번호 검증 후 Access Token 및 Refresh Token 발급
    @Transactional
    public LoginResponseDto login(LoginRequestDto requestDto) {
        // 사용자 조회 및 비밀번호 검증
        User user = validateAndGetUser(requestDto);

        // JWT 토큰 생성 및 반환
        return generateTokenResponse(user);
    }

    // OAuth 로그인 처리: 비밀번호 검증 없이 JWT 토큰 발급
    @Transactional
    public LoginResponseDto oauthLogin(User user) {
        return generateTokenResponse(user);
    }

    // 비밀번호 검증 및 사용자 조회 (일반 로그인 전용)
    private User validateAndGetUser(LoginRequestDto requestDto) {
        // 사용자 조회
        User user = userRepository.findByEmail(requestDto.getEmail())
                .orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다"));

        // 비밀번호 검증
        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다");
        }

        return user;
    }

    // JWT 토큰 생성 공통 로직
    private LoginResponseDto generateTokenResponse(User user) {
        // 토큰 생성
        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getName());
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), user.getId(), user.getName());

        // 기존 Refresh Token 삭제 (한 사용자당 하나의 Refresh Token만 유지)
        refreshTokenRepository.findByUserId(user.getId())
                .ifPresent(refreshTokenRepository::delete);

        // Refresh Token을 DB에 저장
        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000);
        RefreshToken refreshTokenEntity = new RefreshToken(refreshToken, user.getId(), expiryDate);
        refreshTokenRepository.save(refreshTokenEntity);

        // 응답 생성 (expiresIn은 초 단위, 사용자 정보 포함)
        return new LoginResponseDto(
                accessToken,
                refreshToken,
                accessTokenExpiration / 1000,
                user.getId(),
                user.getName()
        );
    }

    // Access Token 갱신: Refresh Token 검증 후 새로운 Access Token 발급
    @Transactional
    public RefreshTokenResponseDto refreshAccessToken(String refreshToken) {
        // DB에서 Refresh Token 조회
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        // 토큰 만료 확인
        if (storedToken.isExpired()) {
            // 만료된 토큰은 DB에서 삭제
            refreshTokenRepository.delete(storedToken);
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        // JWT 토큰 검증
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // 토큰에서 사용자 정보 추출
        String email = jwtUtil.extractEmail(refreshToken);
        Long userId = jwtUtil.extractUserId(refreshToken);

        // 사용자 존재 확인
        userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 새로운 액세스 토큰 생성
        String newAccessToken = jwtUtil.generateAccessToken(email, userId, jwtUtil.extractName(refreshToken));

        // 응답 생성 (expiresIn은 초 단위)
        return new RefreshTokenResponseDto(newAccessToken, accessTokenExpiration / 1000);
    }

    // 로그아웃: Refresh Token 삭제
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
