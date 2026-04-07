package com.capston.demo.domain.ai.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class AssemblyAiTranscriptResult {
    private final String fullText;
    private final List<Utterance> utterances;

    @Getter
    @AllArgsConstructor
    public static class Utterance {
        private final String speaker;
        private final String text;
        private final double startSec;
        private final double endSec;
    }
}
