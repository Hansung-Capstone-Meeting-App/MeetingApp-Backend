package com.capston.demo.domain.meeting.entity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "meeting_transcripts")
@Getter
@Setter
public class MeetingTranscript {

    @Id
    private String id;

    @Indexed
    private Long meetingId;

    private Long recordingId;

    private String fullText;

    private String summary;

    private List<String> keywords = new ArrayList<>();

    private LocalDateTime analyzedAt;

    private LocalDateTime createdAt = LocalDateTime.now();

    private List<SegmentEmbedded> segments = new ArrayList<>();

    private List<SpeakerMappingEmbedded> speakerMappings = new ArrayList<>();

    @Getter
    @Setter
    public static class SegmentEmbedded {
        private String speakerLabel;
        private Long userId;
        private String content;
        private Float startSec;
        private Float endSec;
        private Integer sequence;
    }

    @Getter
    @Setter
    public static class SpeakerMappingEmbedded {
        private String speakerLabel;
        private Long userId;
        private String userName;
    }
}
