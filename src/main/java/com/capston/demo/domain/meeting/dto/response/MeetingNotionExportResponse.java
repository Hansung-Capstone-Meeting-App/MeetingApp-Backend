package com.capston.demo.domain.meeting.dto.response;

import lombok.Getter;

@Getter
public class MeetingNotionExportResponse {

    private final Long meetingId;
    private final String notionPageId;
    private final String notionUrl;
    private final boolean updated;

    public MeetingNotionExportResponse(Long meetingId, String notionPageId, String notionUrl, boolean updated) {
        this.meetingId = meetingId;
        this.notionPageId = notionPageId;
        this.notionUrl = notionUrl;
        this.updated = updated;
    }
}
