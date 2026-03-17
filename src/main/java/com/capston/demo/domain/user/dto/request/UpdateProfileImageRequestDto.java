package com.capston.demo.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileImageRequestDto {

    /**
     * S3에 업로드된 프로필 이미지의 전체 URL 또는 키
     */
    @NotBlank(message = "프로필 이미지 URL은 비어 있을 수 없습니다.")
    private String profileImageUrl;
}

