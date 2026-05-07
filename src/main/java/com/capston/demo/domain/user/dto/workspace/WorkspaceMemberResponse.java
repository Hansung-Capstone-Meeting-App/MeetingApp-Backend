package com.capston.demo.domain.user.dto.workspace;

import com.capston.demo.domain.user.entity.WorkspaceMember;
import lombok.Getter;

@Getter
public class WorkspaceMemberResponse {

    private final Long userId;
    private final String name;
    private final String email;
    private final String profileImg;
    private final String role;

    public WorkspaceMemberResponse(WorkspaceMember member) {
        this.userId = member.getUser().getId();
        this.name = member.getUser().getName();
        this.email = member.getUser().getEmail();
        this.profileImg = member.getUser().getProfileImg();
        this.role = member.getRole().name();
    }
}
