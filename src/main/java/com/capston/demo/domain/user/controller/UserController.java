package com.capston.demo.domain.user.controller;


import com.capston.demo.domain.user.dto.UserProfileDto;
import com.capston.demo.domain.user.dto.request.RegisterRequestDto;
import com.capston.demo.domain.user.dto.response.UserSearchResponse;
import com.capston.demo.domain.user.dto.request.UpdateUserNameRequestDto;
import com.capston.demo.domain.user.dto.request.UpdateProfileImageRequestDto;
import com.capston.demo.domain.user.dto.request.ChangePasswordRequestDto;
import com.capston.demo.domain.user.dto.response.RegisterResponseDto;
import com.capston.demo.domain.user.service.S3Service;
import com.capston.demo.domain.user.service.UserService;
import com.capston.demo.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 사용자 관리 컨트롤러 (모바일 앱용 REST API)
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final S3Service s3Service;

    /**
     * 회원가입 API
     *
     * @param requestDto 회원가입 정보
     * @return 생성된 사용자 정보
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDto> register(@Valid @RequestBody RegisterRequestDto requestDto) {
        RegisterResponseDto response = userService.registerUser(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 사용자 프로필 조회 API
     *
     * @param userDetails 인증된 사용자 정보 (JWT에서 자동 추출)
     * @return 사용자 프로필 정보
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getUserProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getUserId();
        UserProfileDto profile = userService.getUserProfile(userId);
        return ResponseEntity.ok(profile);
    }

    /**
     * 사용자 이름 변경 API
     *
     * @param userDetails 인증된 사용자 정보
     * @param requestDto 변경할 이름
     * @return 상태 코드 204 (내용 없음)
     */
    @PatchMapping("/profile/name")
    public ResponseEntity<Void> updateUserName(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateUserNameRequestDto requestDto
    ) {
        Long userId = userDetails.getUserId();
        userService.updateUserName(userId, requestDto);
        return ResponseEntity.noContent().build();
    }

    /**
     * 프로필 이미지 변경 API
     *
     * @param userDetails 인증된 사용자 정보
     * @param requestDto 변경할 프로필 이미지 URL
     * @return 상태 코드 204 (내용 없음)
     */
    @PatchMapping("/profile/image")
    public ResponseEntity<Void> updateProfileImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileImageRequestDto requestDto
    ) {
        Long userId = userDetails.getUserId();
        userService.updateProfileImage(userId, requestDto);
        return ResponseEntity.noContent().build();
    }

    /**
     * 계정 탈퇴 API (Soft delete)
     *
     * @param userDetails 인증된 사용자 정보
     * @return 상태 코드 204 (내용 없음)
     */
    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();
        userService.deleteAccount(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 비밀번호 변경 API
     *
     * @param userDetails 인증된 사용자 정보
     * @param requestDto 현재 비밀번호와 새 비밀번호
     * @return 상태 코드 204 (내용 없음)
     */
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequestDto requestDto
    ) {
        Long userId = userDetails.getUserId();
        userService.changePassword(userId, requestDto);
        return ResponseEntity.noContent().build();
    }

    // GET /api/users/search?q=검색어
    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(userService.searchUsers(q));
    }

    /**
     * S3 Presigned URL 발급 API
     * 프로필 이미지 업로드를 위한 임시 URL 생성
     *
     * @param filename 업로드할 파일명
     * @return Presigned URL
     */
    @GetMapping("/presigned-url")
    public ResponseEntity<Map<String, String>> getPresignedUrl(@RequestParam String filename) {
        String presignedUrl = s3Service.createPresignedUrl("profileImg/" + filename);
        return ResponseEntity.ok(Map.of("presignedUrl", presignedUrl));
    }
}
