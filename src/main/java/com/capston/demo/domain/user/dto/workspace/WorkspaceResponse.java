package com.capston.demo.domain.user.dto.workspace;

import com.capston.demo.domain.user.entity.Workspace;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class WorkspaceResponse {

    private final Long id;
    private final String name;
    private final String slug;
    private final Long ownerId;
    private final String ownerName;
    private final LocalDateTime createdAt;

    public WorkspaceResponse(Workspace workspace) {
        this.id = workspace.getId();
        this.name = workspace.getName();
        this.slug = workspace.getSlug();
        this.ownerId = workspace.getOwner().getId();
        this.ownerName = workspace.getOwner().getName();
        this.createdAt = workspace.getCreatedAt();
    }
}
