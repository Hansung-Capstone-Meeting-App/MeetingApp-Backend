package com.capston.demo.domain.user.dto.response;

import lombok.Getter;

/**
 * GET /api/oauth2/notion/status 응답.
 * id·configured 는 프론트 분기용, name·url 은 설정 화면 표시·Notion 열기용(조회 실패 시 null).
 */
@Getter
public class NotionStatusResponse {

    private final boolean linked;
    private final String notionName;

    private final boolean parentPageConfigured;
    private final String parentPageId;
    private final String parentPageName;
    private final String parentPageUrl;

    private final boolean calendarConfigured;
    private final String calendarDatabaseId;
    private final String calendarName;
    private final String calendarUrl;

    private final boolean meetingNotesConfigured;
    private final String meetingNotesDatabaseId;
    private final String meetingNotesName;
    private final String meetingNotesUrl;

    /** linked + parent + calendar + meetingNotes 모두 설정됨 */
    private final boolean ready;

    public NotionStatusResponse(boolean linked,
                                String notionName,
                                boolean parentPageConfigured,
                                String parentPageId,
                                String parentPageName,
                                String parentPageUrl,
                                boolean calendarConfigured,
                                String calendarDatabaseId,
                                String calendarName,
                                String calendarUrl,
                                boolean meetingNotesConfigured,
                                String meetingNotesDatabaseId,
                                String meetingNotesName,
                                String meetingNotesUrl) {
        this.linked = linked;
        this.notionName = notionName;
        this.parentPageConfigured = parentPageConfigured;
        this.parentPageId = parentPageId;
        this.parentPageName = parentPageName;
        this.parentPageUrl = parentPageUrl;
        this.calendarConfigured = calendarConfigured;
        this.calendarDatabaseId = calendarDatabaseId;
        this.calendarName = calendarName;
        this.calendarUrl = calendarUrl;
        this.meetingNotesConfigured = meetingNotesConfigured;
        this.meetingNotesDatabaseId = meetingNotesDatabaseId;
        this.meetingNotesName = meetingNotesName;
        this.meetingNotesUrl = meetingNotesUrl;
        this.ready = linked && parentPageConfigured && calendarConfigured && meetingNotesConfigured;
    }

    public static NotionStatusResponse notLinked() {
        return new NotionStatusResponse(
                false, null,
                false, null, null, null,
                false, null, null, null,
                false, null, null, null
        );
    }
}
