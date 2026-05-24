package com.capston.demo.domain.user.controllerDocs;

import com.capston.demo.domain.user.dto.request.LoginRequestDto;
import com.capston.demo.domain.user.dto.request.LogoutRequestDto;
import com.capston.demo.domain.user.dto.request.RefreshTokenRequestDto;
import com.capston.demo.domain.user.dto.response.LoginResponseDto;
import com.capston.demo.domain.user.dto.response.RefreshTokenResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "이메일/비밀번호 로그인, 토큰 갱신, 로그아웃 API")
public interface AuthControllerDocs {

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호로 로그인하여 JWT 액세스 토큰과 리프레시 토큰을 발급합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "email": "user@example.com",
                                              "password": "password123"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "로그인 성공"),
                    @ApiResponse(responseCode = "400", description = "유효하지 않은 요청"),
                    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
            }
    )
    ResponseEntity<LoginResponseDto> login(LoginRequestDto requestDto);

    @Operation(
            summary = "액세스 토큰 갱신",
            description = "리프레시 토큰으로 새로운 액세스 토큰을 발급합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefreshTokenRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "갱신 성공"),
                    @ApiResponse(responseCode = "401", description = "만료되었거나 유효하지 않은 리프레시 토큰")
            }
    )
    ResponseEntity<RefreshTokenResponseDto> refresh(RefreshTokenRequestDto requestDto);

    @Operation(
            summary = "로그아웃",
            description = "서버에 저장된 해당 사용자의 리프레시 토큰을 삭제합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LogoutRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "userId": 1
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "로그아웃 성공")
            }
    )
    ResponseEntity<Void> logout(LogoutRequestDto requestDto);
}
