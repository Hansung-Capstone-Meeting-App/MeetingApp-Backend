package com.capston.demo.domain.user.dto.workspace;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class WorkspaceInviteRequest {

    @NotBlank
    @Email
    private String email;
}
