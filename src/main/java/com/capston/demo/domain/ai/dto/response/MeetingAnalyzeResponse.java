package com.capston.demo.domain.ai.dto.response;

import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public class MeetingAnalyzeResponse {
    private Map<String, String> fieldDescriptions = Map.of(
            "originalFullText", "originalFullText(AssemblyAI 원문 전체 전사)",
            "correctedFullText", "correctedFullText(Gemini 교정 전체 전사)",
            "displayFullText", "displayFullText(교정본에 원문을 괄호로 표시한 전사)",
            "summary", "summary(회의 요약)",
            "keywords", "keywords(핵심 키워드)",
            "savedTaskCount", "savedTaskCount(저장된 할 일 수)",
            "savedEventCount", "savedEventCount(저장된 일정 수)"
    );

    private String transcriptId;
    private String originalFullText;
    private String correctedFullText;
    private String displayFullText;
    private String summary;
    private List<String> keywords;
    private int savedTaskCount;
    private int savedEventCount;

    public MeetingAnalyzeResponse(String transcriptId, String originalFullText, String correctedFullText,
                                  String displayFullText, String summary, List<String> keywords,
                                  int savedTaskCount, int savedEventCount) {
        this.transcriptId = transcriptId;
        this.originalFullText = originalFullText;
        this.correctedFullText = correctedFullText;
        this.displayFullText = displayFullText;
        this.summary = summary;
        this.keywords = keywords;
        this.savedTaskCount = savedTaskCount;
        this.savedEventCount = savedEventCount;
    }
}
