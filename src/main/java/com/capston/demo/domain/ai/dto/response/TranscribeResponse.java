package com.capston.demo.domain.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;
import java.util.Map;

@Getter
public class TranscribeResponse {
    private Map<String, String> fieldDescriptions = Map.of(
            "content", "content(표시 문장)",
            "originalContent", "originalContent(원본)",
            "correctedContent", "correctedContent(교정본)",
            "correctionChanged", "correctionChanged(실질 교정 여부)",
            "correctionStatusText", "correctionStatusText(교정 상태)"
    );
    private String transcriptId;
    private String originalFullText;
    private String correctedFullText;
    private List<SegmentInfo> segments;

    public TranscribeResponse(String transcriptId, String originalFullText, String correctedFullText,
                              List<SegmentInfo> segments) {
        this.transcriptId = transcriptId;
        this.originalFullText = originalFullText;
        this.correctedFullText = correctedFullText;
        this.segments = segments;
    }

    @Getter
    public static class SegmentInfo {
        private String speakerLabel;
        private String content;
        private String originalContent;
        private String correctedContent;
        private boolean correctionChanged;
        private String correctionStatusText;
        private float startSec;
        private float endSec;

        public SegmentInfo(String speakerLabel, String content, String originalContent, String correctedContent,
                           boolean correctionChanged, float startSec, float endSec) {
            this.speakerLabel = speakerLabel;
            this.content = content;
            this.originalContent = originalContent;
            this.correctedContent = correctedContent;
            this.correctionChanged = correctionChanged;
            this.correctionStatusText = correctionChanged ? "교정됨" : "원문 유지";
            this.startSec = startSec;
            this.endSec = endSec;
        }
    }
}
