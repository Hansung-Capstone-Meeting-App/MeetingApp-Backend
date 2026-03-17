package com.capston.demo.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class LoginResponseDto {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private Long expiresIn;
    // 프론트에서 바로 사용할 수 있도록 사용자 정보 포함
    private Long userId;
    private String name;

    public LoginResponseDto(String accessToken, String refreshToken, Long expiresIn, Long userId, String name) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.userId = userId;
        this.name = name;
    }
}
