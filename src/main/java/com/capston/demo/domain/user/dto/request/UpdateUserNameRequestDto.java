package com.capston.demo.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserNameRequestDto {

    @NotBlank(message = "이름은 비어 있을 수 없습니다.")
    private String name;
}

