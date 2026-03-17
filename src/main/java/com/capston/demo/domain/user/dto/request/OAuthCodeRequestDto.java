package com.capston.demo.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OAuthCodeRequestDto {
    private String code;
}

