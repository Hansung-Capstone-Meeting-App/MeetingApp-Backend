package com.capston.demo.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RefreshTokenResponseDto {

    private String accessToken;
    private String tokenType = "Bearer";
    private Long expiresIn;

    public RefreshTokenResponseDto(String accessToken, Long expiresIn) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
    }
}
