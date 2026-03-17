package com.capston.demo.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private Long id;
    private String email;
    private String name;
    private String profileImg;
    private String status;
    private LocalDateTime createdAt;

    // OAuth 정보
    private String oauthProvider;
    private String oauthEmail;
    private LocalDateTime oauthLinkedAt;
}
