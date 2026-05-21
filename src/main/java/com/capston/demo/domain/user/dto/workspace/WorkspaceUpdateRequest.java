package com.capston.demo.domain.user.dto.workspace;

import lombok.Getter;

@Getter
public class WorkspaceUpdateRequest {
    private String name;
    private String meetingCategory;
    private String meetingContext;
}
