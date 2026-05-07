package com.capston.demo.domain.user.dto.workspace;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class WorkspaceCreateRequest {

    @NotBlank
    private String name;
}
