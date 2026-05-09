package com.capston.demo.domain.user.dto.workspace;

import com.capston.demo.domain.user.entity.WorkspaceInvitation;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class InvitationResponse {

    private final Long id;
    private final Long workspaceId;
    private final String workspaceName;
    private final String invitedByName;
    private final String status;
    private final LocalDateTime createdAt;

    public InvitationResponse(WorkspaceInvitation invitation) {
        this.id = invitation.getId();
        this.workspaceId = invitation.getWorkspace().getId();
        this.workspaceName = invitation.getWorkspace().getName();
        this.invitedByName = invitation.getInvitedBy().getName();
        this.status = invitation.getStatus().name();
        this.createdAt = invitation.getCreatedAt();
    }
}
