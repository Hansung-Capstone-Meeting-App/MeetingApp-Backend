package com.capston.demo.domain.ai.dto.internal;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GeminiCorrectionResult {
    private final String correctedFullText;
    private final String displayFullText;
    private final List<CorrectedUtterance> utterances;

    @Getter
    @AllArgsConstructor
    public static class CorrectedUtterance {
        private final String speakerLabel;
        private final String originalText;
        private final String correctedText;
        private final String displayText;
        private final List<CorrectionItem> corrections;
        private final double startSec;
        private final double endSec;
    }

    @Getter
    @AllArgsConstructor
    public static class CorrectionItem {
        private final String original;
        private final String corrected;
        private final String reason;
    }
}
