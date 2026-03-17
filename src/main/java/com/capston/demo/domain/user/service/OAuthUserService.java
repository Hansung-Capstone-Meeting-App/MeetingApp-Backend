package com.capston.demo.domain.user.service;

import com.capston.demo.domain.user.dto.OAuthUserInfo;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

// OAuth 제공자(Google, Notion 등)에서 가져온 사용자 정보를
// 우리 서비스의 User 엔티티와 연결/생성하는 공통 서비스
@Service
@RequiredArgsConstructor
public class OAuthUserService {

    private final UserRepository userRepository;

    // OAuth 사용자 처리 (존재하면 업데이트, 없으면 새로 생성)
    public User processOAuthUser(OAuthUserInfo userInfo) {
        String email = userInfo.getEmail();
        if (email == null || email.isBlank()) { //이메일이 없으면 내부 식별용 이메일을 생성
            // 일부 OAuth 제공자(Notion 등)는 이메일을 제공하지 않을 수 있어, 내부 식별용 이메일을 생성
            String provider = (userInfo.getProvider() == null || userInfo.getProvider().isBlank()) //제공자가 없으면 oauth로 설정
                    ? "oauth"
                    : userInfo.getProvider();
            String providerId = (userInfo.getProviderId() == null || userInfo.getProviderId().isBlank()) //제공자 ID가 없으면 랜덤 UUID로 설정
                    ? UUID.randomUUID().toString()
                    : userInfo.getProviderId();
            email = provider + "-" + providerId + "@oauth.local";
        }

        String finalEmail = email;
        return userRepository.findByEmail(finalEmail) //이메일로 사용자 조회
                .map(existingUser -> { //기존 사용자가 있으면 업데이트
                    // 기존 사용자에 OAuth 제공자 정보만 덮어씌움 (계정 연결)
                    existingUser.setOauthProvider(userInfo.getProvider());
                    existingUser.setOauthProviderId(userInfo.getProviderId());
                    existingUser.setOauthLinkedAt(LocalDateTime.now());
                    if (userInfo.getPicture() != null && !userInfo.getPicture().isEmpty()) {
                        existingUser.setProfileImg(userInfo.getPicture());
                    }
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> { //기존 사용자가 없으면 새로 생성
                    User newUser = new User();
                    newUser.setEmail(finalEmail);
                    newUser.setName(userInfo.getName());
                    // OAuth로만 로그인 가능한 계정이라 랜덤 비밀번호 설정 (일반 로그인 불가)
                    newUser.setPassword(UUID.randomUUID().toString());
                    newUser.setProfileImg(userInfo.getPicture());
                    newUser.setOauthProvider(userInfo.getProvider());
                    newUser.setOauthProviderId(userInfo.getProviderId());
                    newUser.setOauthLinkedAt(LocalDateTime.now());
                    return userRepository.save(newUser);
                });
    }
}

