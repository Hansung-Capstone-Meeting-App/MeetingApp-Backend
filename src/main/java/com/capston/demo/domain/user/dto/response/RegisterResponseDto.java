package com.capston.demo.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class RegisterResponseDto {

    private Long id;
    private String email;
    private String name;
    private LocalDateTime createdAt;
}
