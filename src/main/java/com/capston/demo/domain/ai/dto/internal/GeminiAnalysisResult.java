package com.capston.demo.domain.ai.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class GeminiAnalysisResult {
    private final String summary;
    private final List<String> keywords;
    private final List<ExtractedTask> tasks;
    private final List<ExtractedEvent> events;

    @Getter
    @AllArgsConstructor
    public static class SpeakerInfo {
        private final String speakerLabel;
        private final Long userId;
        private final String userName;
    }

    @Getter
    @AllArgsConstructor
    public static class ExtractedTask {
        private final String speakerLabel;
        private final String assigneeName;
        private final String title;
        private final String description;
        private final String dueDate;
    }

    @Getter
    @AllArgsConstructor
    public static class ExtractedEvent {
        private final String speakerLabel;
        private final Long userId;
        private final String createdByName;
        private final List<Long> participantUserIds;
        private final String title;
        private final String description;
        private final String location;
        private final String startAt;
        private final String endAt;
        private final Boolean isAllDay;
    }
}
