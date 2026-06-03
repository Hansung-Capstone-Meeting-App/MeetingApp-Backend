package com.capston.demo.domain.user.dto.response;

import lombok.Getter;

@Getter
public class NotionStatusResponse {

    private final boolean linked;
    private final boolean calendarConfigured;
    private final String notionName;
    private final String calendarName;
    private final boolean meetingNotesConfigured;
    private final String meetingNotesName;
    private final boolean rootPageConfigured;
    private final String rootPageName;

    public NotionStatusResponse(boolean linked,
                                boolean calendarConfigured,
                                String notionName,
                                String calendarName,
                                boolean meetingNotesConfigured,
                                String meetingNotesName,
                                boolean rootPageConfigured,
                                String rootPageName) {
        this.linked = linked;
        this.calendarConfigured = calendarConfigured;
        this.notionName = notionName;
        this.calendarName = calendarName;
        this.meetingNotesConfigured = meetingNotesConfigured;
        this.meetingNotesName = meetingNotesName;
        this.rootPageConfigured = rootPageConfigured;
        this.rootPageName = rootPageName;
    }

    public static NotionStatusResponse notLinked() {
        return new NotionStatusResponse(false, false, null, null, false, null, false, null);
    }
}
