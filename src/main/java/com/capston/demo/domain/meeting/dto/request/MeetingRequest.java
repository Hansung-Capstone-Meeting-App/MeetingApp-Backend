package com.capston.demo.domain.meeting.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MeetingRequest {

    private Long workspaceId;
    private String title;
}