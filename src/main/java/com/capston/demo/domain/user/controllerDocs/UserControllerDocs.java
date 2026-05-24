package com.capston.demo.domain.user.controllerDocs;

import com.capston.demo.domain.user.dto.UserProfileDto;
import com.capston.demo.domain.user.dto.request.ChangePasswordRequestDto;
import com.capston.demo.domain.user.dto.request.RegisterRequestDto;
import com.capston.demo.domain.user.dto.request.UpdateProfileImageRequestDto;
import com.capston.demo.domain.user.dto.request.UpdateUserNameRequestDto;
import com.capston.demo.domain.user.dto.response.RegisterResponseDto;
import com.capston.demo.domain.user.dto.response.UserSearchResponse;
import com.capston.demo.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.Map;

@Tag(name = "User", description = "회원가입, 프로필 조회/수정, 계정 탈퇴 API")
public interface UserControllerDocs {

    @Operation(
            summary = "회원가입",
            description = "이메일·비밀번호·이름으로 새 계정을 생성합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegisterRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "email": "user@example.com",
                                              "password": "password123",
                                              "displayName": "홍길동",
                                              "profileImageUrl": "https://example.com/profile.jpg"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "가입 성공"),
                    @ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
                    @ApiResponse(responseCode = "409", description = "이미 사용 중인 이메일")
            }
    )
    ResponseEntity<RegisterResponseDto> register(RegisterRequestDto requestDto);

    @Operation(
            summary = "내 프로필 조회",
            description = "JWT로 인증된 현재 사용자의 프로필 정보를 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 필요")
            }
    )
    ResponseEntity<UserProfileDto> getUserProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(
            summary = "이름 변경",
            description = "현재 사용자의 표시 이름(displayName)을 변경합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateUserNameRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "name": "새 이름"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "204", description = "변경 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 필요")
            }
    )
    ResponseEntity<Void> updateUserName(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            UpdateUserNameRequestDto requestDto);

    @Operation(
            summary = "프로필 이미지 변경",
            description = "S3에 업로드한 이미지 URL로 프로필 이미지를 변경합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateProfileImageRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "profileImageUrl": "https://bucket.s3.amazonaws.com/profileImg/avatar.jpg"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "204", description = "변경 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 필요")
            }
    )
    ResponseEntity<Void> updateProfileImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            UpdateProfileImageRequestDto requestDto);

    @Operation(
            summary = "계정 탈퇴",
            description = "현재 사용자 계정을 Soft delete 처리합니다.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "탈퇴 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 필요")
            }
    )
    ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(
            summary = "비밀번호 변경",
            description = "현재 비밀번호 확인 후 새 비밀번호로 변경합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ChangePasswordRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "currentPassword": "oldpassword",
                                              "newPassword": "newpassword123"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "204", description = "변경 성공"),
                    @ApiResponse(responseCode = "400", description = "현재 비밀번호 불일치"),
                    @ApiResponse(responseCode = "401", description = "인증 필요")
            }
    )
    ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            ChangePasswordRequestDto requestDto);

    @Operation(
            summary = "사용자 검색",
            description = "이름 또는 이메일로 사용자를 검색합니다. 워크스페이스 멤버 초대 등에 사용합니다.",
            parameters = {
                    @Parameter(name = "q", description = "검색어 (이름/이메일)", example = "hong", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "검색 성공")
            }
    )
    ResponseEntity<List<UserSearchResponse>> searchUsers(String q);

    @Operation(
            summary = "프로필 이미지 Presigned URL 발급",
            description = "S3에 프로필 이미지를 직접 업로드하기 위한 Presigned URL을 발급합니다.",
            parameters = {
                    @Parameter(name = "filename", description = "업로드할 파일명", example = "avatar.jpg", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "발급 성공")
            }
    )
    ResponseEntity<Map<String, String>> getPresignedUrl(String filename);
}
