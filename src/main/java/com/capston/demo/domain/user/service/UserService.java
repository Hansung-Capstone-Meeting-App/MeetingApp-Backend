package com.capston.demo.domain.user.service;

import com.capston.demo.domain.user.dto.request.RegisterRequestDto;
import com.capston.demo.domain.user.dto.response.RegisterResponseDto;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.repository.UserRepository;
import com.capston.demo.global.exception.DuplicateEmailException;
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
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다");
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
}
