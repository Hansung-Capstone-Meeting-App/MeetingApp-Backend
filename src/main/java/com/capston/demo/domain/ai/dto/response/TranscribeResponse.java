package com.capston.demo.domain.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class TranscribeResponse {
    private Long transcriptId;
    private List<SegmentInfo> segments;

    @Getter
    @AllArgsConstructor
    public static class SegmentInfo {
        private String speakerLabel;
        private String content;
        private float startSec;
        private float endSec;
    }
}
