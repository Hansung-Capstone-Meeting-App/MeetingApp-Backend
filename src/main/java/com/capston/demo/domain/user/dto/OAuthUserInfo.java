package com.capston.demo.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthUserInfo {
    private String providerId;
    private String email;
    private String name;
    private String picture;
    private String provider;
}
