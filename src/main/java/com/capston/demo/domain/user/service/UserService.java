package com.capston.demo.domain.user.service;


import com.capston.demo.domain.user.dto.UserProfileDto;
import com.capston.demo.domain.user.dto.request.RegisterRequestDto;
import com.capston.demo.domain.user.dto.request.UpdateUserNameRequestDto;
import com.capston.demo.domain.user.dto.request.UpdateProfileImageRequestDto;
import com.capston.demo.domain.user.dto.request.ChangePasswordRequestDto;
import com.capston.demo.domain.user.dto.response.RegisterResponseDto;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.repository.UserRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import org.springframework.security.authentication.BadCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponseDto registerUser(RegisterRequestDto requestDto) {
        // 중복 이메일 체크
        if (userRepository.findByEmail(requestDto.getEmail()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        // User 엔티티 생성
        User user = new User();
        user.setEmail(requestDto.getEmail());
        user.setPassword(passwordEncoder.encode(requestDto.getPassword()));
        user.setName(requestDto.getDisplayName());
        // 이미지 URL이 있으면 저장, 없으면 null 혹은 기본 이미지 설정
        if (requestDto.getProfileImageUrl() != null && !requestDto.getProfileImageUrl().isEmpty()) {
            user.setProfileImg(requestDto.getProfileImageUrl());
        }
        // 저장
        User savedUser = userRepository.save(user);

        // 응답 DTO 생성 (비밀번호 제외)
        return new RegisterResponseDto(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getCreatedAt()
        );
    }

    /**
     * 사용자 프로필 조회 (OAuth 정보 포함)
     */
    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .profileImg(user.getProfileImg())
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .createdAt(user.getCreatedAt())
                .oauthProvider(user.getOauthProvider())
                .oauthEmail(user.getEmail()) // OAuth 이메일은 일반 이메일과 동일
                .oauthLinkedAt(user.getOauthLinkedAt())
                .build();
    }

    /**
     * 사용자 이름 변경
     */
    @Transactional
    public void updateUserName(Long userId, UpdateUserNameRequestDto requestDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.setName(requestDto.getName());
    }

    /**
     * 프로필 이미지 변경
     */
    @Transactional
    public void updateProfileImage(Long userId, UpdateProfileImageRequestDto requestDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.setProfileImg(requestDto.getProfileImageUrl());
    }

    /**
     * 계정 탈퇴 (Soft delete: status만 변경)
     */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.setStatus(User.UserStatus.deleted);
    }

    /**
     * 비밀번호 변경
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequestDto requestDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 현재 비밀번호 검증
        if (!passwordEncoder.matches(requestDto.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("현재 비밀번호가 올바르지 않습니다");
        }

        // 새 비밀번호 설정
        user.setPassword(passwordEncoder.encode(requestDto.getNewPassword()));
    }
}
