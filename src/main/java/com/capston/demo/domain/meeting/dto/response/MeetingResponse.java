package com.capston.demo.domain.meeting.dto.response;

import com.capston.demo.domain.meeting.entity.Meeting;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MeetingResponse {

    private final Long id;
    private final Long workspaceId;
    private final String title;
    private final Long createdBy;
    private final LocalDateTime createdAt;

    public MeetingResponse(Meeting meeting) {
        this.id = meeting.getId();
        this.workspaceId = meeting.getWorkspaceId();
        this.title = meeting.getTitle();
        this.createdBy = meeting.getCreatedBy();
        this.createdAt = meeting.getCreatedAt();
    }
}