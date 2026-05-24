package com.capston.demo.domain.meeting.dto.export;

import lombok.Getter;

import java.util.List;

/**
 * 대화록 PDF 템플릿(meeting-export/transcript.html)에 바인딩하는 뷰 모델.
 * Thymeleaf 변수명: {@code transcript}
 */
@Getter
public class MeetingTranscriptExportModel {

    private final String workspaceName;
    private final String meetingTitle;
    private final String meetingCreatedAt;
    private final String transcribedAt;
    private final boolean includeTimestamps;
    private final List<SegmentRow> segments;
    private final String exportedAt;

    public MeetingTranscriptExportModel(String workspaceName, String meetingTitle, String meetingCreatedAt,
                                        String transcribedAt, boolean includeTimestamps,
                                        List<SegmentRow> segments, String exportedAt) {
        this.workspaceName = workspaceName;
        this.meetingTitle = meetingTitle;
        this.meetingCreatedAt = meetingCreatedAt;
        this.transcribedAt = transcribedAt;
        this.includeTimestamps = includeTimestamps;
        this.segments = segments;
        this.exportedAt = exportedAt;
    }

    @Getter
    public static class SegmentRow {
        private final String speakerName;
        private final String timestamp;
        private final String content;

        public SegmentRow(String speakerName, String timestamp, String content) {
            this.speakerName = speakerName;
            this.timestamp = timestamp;
            this.content = content;
        }
    }
}
