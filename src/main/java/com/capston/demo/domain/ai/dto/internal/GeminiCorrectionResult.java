package com.capston.demo.domain.ai.dto.internal;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GeminiCorrectionResult {
    private final String correctedFullText;
    private final List<CorrectedUtterance> utterances;

    @Getter
    @AllArgsConstructor
    public static class CorrectedUtterance {
        private final String speakerLabel;
        private final String originalText;
        private final String correctedText;
        private final double startSec;
        private final double endSec;
    }
}
