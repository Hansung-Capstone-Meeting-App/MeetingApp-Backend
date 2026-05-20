package com.capston.demo.domain.ai.dto.response;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public class TranscribeResponse {
    private Map<String, String> fieldDescriptions = Map.of(
            "content", "content(분석에 사용하는 교정 문장)",
            "originalContent", "originalContent(AssemblyAI 원문)",
            "correctedContent", "correctedContent(Gemini 교정본)",
            "displayContent", "displayContent(교정본에 원문을 괄호로 표시)",
            "corrections", "corrections(교정된 단어 목록)",
            "correctionChanged", "correctionChanged(실질 교정 여부)",
            "correctionStatusText", "correctionStatusText(교정 상태)"
    );

    private String transcriptId;
    private String originalFullText;
    private String correctedFullText;
    private String displayFullText;
    private List<SegmentInfo> segments;

    public TranscribeResponse(String transcriptId, String originalFullText, String correctedFullText,
                              String displayFullText, List<SegmentInfo> segments) {
        this.transcriptId = transcriptId;
        this.originalFullText = originalFullText;
        this.correctedFullText = correctedFullText;
        this.displayFullText = displayFullText;
        this.segments = segments;
    }

    @Getter
    public static class SegmentInfo {
        private String speakerLabel;
        private String content;
        private String originalContent;
        private String correctedContent;
        private String displayContent;
        private List<CorrectionInfo> corrections;
        private boolean correctionChanged;
        private String correctionStatusText;
        private float startSec;
        private float endSec;

        public SegmentInfo(String speakerLabel, String content, String originalContent, String correctedContent,
                           String displayContent, List<CorrectionInfo> corrections,
                           boolean correctionChanged, float startSec, float endSec) {
            this.speakerLabel = speakerLabel;
            this.content = content;
            this.originalContent = originalContent;
            this.correctedContent = correctedContent;
            this.displayContent = displayContent;
            this.corrections = corrections;
            this.correctionChanged = correctionChanged;
            this.correctionStatusText = correctionChanged ? "교정됨" : "원문 유지";
            this.startSec = startSec;
            this.endSec = endSec;
        }
    }

    @Getter
    @AllArgsConstructor
    public static class CorrectionInfo {
        private String original;
        private String corrected;
        private String reason;
    }
}
