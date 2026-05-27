package com.capston.demo.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OAuthCodeRequestDto {
    private String code;
    /** web | mobile — auth-url 과 동일한 redirect_uri 로 code 교환 (기본 mobile) */
    private String client;
}

