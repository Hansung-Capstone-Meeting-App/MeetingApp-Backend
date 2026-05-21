package com.capston.demo.domain.meeting.dto.response;

import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class TranscriptResponse {

    private final String id;
    private final Long meetingId;
    private final Long recordingId;
    private final String fullText;
    private final String originalFullText;
    private final String correctedFullText;
    private final String displayFullText;
    private final String summary;
    private final List<String> keywords;
    private final LocalDateTime analyzedAt;
    private final LocalDateTime createdAt;
    private final Map<String, String> fieldDescriptions;
    private final List<SegmentResponse> segments;

    public TranscriptResponse(MeetingTranscript transcript) {
        this.id = transcript.getId();
        this.meetingId = transcript.getMeetingId();
        this.recordingId = transcript.getRecordingId();
        this.fullText = transcript.getFullText();
        this.originalFullText = transcript.getOriginalFullText();
        this.correctedFullText = transcript.getCorrectedFullText();
        this.displayFullText = transcript.getDisplayFullText();
        this.summary = transcript.getSummary();
        this.keywords = transcript.getKeywords();
        this.analyzedAt = transcript.getAnalyzedAt();
        this.createdAt = transcript.getCreatedAt();
        this.fieldDescriptions = Map.of(
                "content", "content(분석에 사용하는 교정 문장)",
                "originalContent", "originalContent(AssemblyAI 원문)",
                "correctedContent", "correctedContent(Gemini 교정본)",
                "displayContent", "displayContent(교정본에 원문을 괄호로 표시)",
                "corrections", "corrections(교정된 단어 목록)",
                "correctionChanged", "correctionChanged(실질 교정 여부)",
                "correctionStatusText", "correctionStatusText(교정 상태)"
        );

        Map<String, String> labelToName = transcript.getSpeakerMappings().stream()
                .filter(m -> m.getUserName() != null)
                .collect(Collectors.toMap(
                        MeetingTranscript.SpeakerMappingEmbedded::getSpeakerLabel,
                        MeetingTranscript.SpeakerMappingEmbedded::getUserName,
                        (a, b) -> a
                ));

        this.segments = transcript.getSegments().stream()
                .map(s -> new SegmentResponse(s, labelToName.get(s.getSpeakerLabel())))
                .collect(Collectors.toList());
    }

    @Getter
    public static class SegmentResponse {
        private final String speakerLabel;
        private final String speakerName;
        private final Long userId;
        private final String content;
        private final String originalContent;
        private final String correctedContent;
        private final String displayContent;
        private final List<CorrectionResponse> corrections;
        private final boolean correctionChanged;
        private final String correctionStatusText;
        private final Float startSec;
        private final Float endSec;
        private final Integer sequence;

        public SegmentResponse(MeetingTranscript.SegmentEmbedded segment, String speakerName) {
            this.speakerLabel = segment.getSpeakerLabel();
            this.speakerName = speakerName;
            this.userId = segment.getUserId();
            this.content = segment.getContent();
            this.originalContent = segment.getOriginalContent();
            this.correctedContent = segment.getCorrectedContent();
            this.displayContent = segment.getDisplayContent();
            this.corrections = segment.getCorrections().stream()
                    .map(CorrectionResponse::new)
                    .collect(Collectors.toList());
            this.correctionChanged = hasMeaningfulCorrection(segment.getOriginalContent(), segment.getCorrectedContent());
            this.correctionStatusText = this.correctionChanged ? "교정됨" : "원문 유지";
            this.startSec = segment.getStartSec();
            this.endSec = segment.getEndSec();
            this.sequence = segment.getSequence();
        }

        private boolean hasMeaningfulCorrection(String original, String corrected) {
            if (original == null || corrected == null) {
                return false;
            }
            return !normalizeForMeaningComparison(original).equals(normalizeForMeaningComparison(corrected));
        }

        private String normalizeForMeaningComparison(String value) {
            return value.replaceAll("[\\s\\p{P}]+", "");
        }
    }

    @Getter
    public static class CorrectionResponse {
        private final String original;
        private final String corrected;
        private final String reason;

        public CorrectionResponse(MeetingTranscript.CorrectionEmbedded correction) {
            this.original = correction.getOriginal();
            this.corrected = correction.getCorrected();
            this.reason = correction.getReason();
        }
    }
}
