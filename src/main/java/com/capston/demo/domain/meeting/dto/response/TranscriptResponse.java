package com.capston.demo.domain.meeting.dto.response;

import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class TranscriptResponse {

    private final String id;
    private final Long meetingId;
    private final Long recordingId;
    private final String fullText;
    private final String summary;
    private final List<String> keywords;
    private final LocalDateTime analyzedAt;
    private final LocalDateTime createdAt;
    private final List<SegmentResponse> segments;

    public TranscriptResponse(MeetingTranscript transcript) {
        this.id = transcript.getId();
        this.meetingId = transcript.getMeetingId();
        this.recordingId = transcript.getRecordingId();
        this.fullText = transcript.getFullText();
        this.summary = transcript.getSummary();
        this.keywords = transcript.getKeywords();
        this.analyzedAt = transcript.getAnalyzedAt();
        this.createdAt = transcript.getCreatedAt();
        this.segments = transcript.getSegments().stream()
                .map(SegmentResponse::new)
                .collect(Collectors.toList());
    }

    @Getter
    public static class SegmentResponse {
        private final String speakerLabel;
        private final Long userId;
        private final String content;
        private final Float startSec;
        private final Float endSec;
        private final Integer sequence;

        public SegmentResponse(MeetingTranscript.SegmentEmbedded segment) {
            this.speakerLabel = segment.getSpeakerLabel();
            this.userId = segment.getUserId();
            this.content = segment.getContent();
            this.startSec = segment.getStartSec();
            this.endSec = segment.getEndSec();
            this.sequence = segment.getSequence();
        }
    }
}
