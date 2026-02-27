package com.capston.demo.domain.user.repository;

import com.capston.demo.domain.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // 토큰으로 조회
    Optional<RefreshToken> findByToken(String token);

    // 사용자 ID로 조회
    Optional<RefreshToken> findByUserId(Long userId);

    // 사용자 ID로 삭제 (로그아웃 시 사용)
    void deleteByUserId(Long userId);

    // 토큰으로 삭제
    void deleteByToken(String token);
}
