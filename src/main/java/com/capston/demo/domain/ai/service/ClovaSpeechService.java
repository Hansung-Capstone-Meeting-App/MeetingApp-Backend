package com.capston.demo.domain.ai.service;

import com.capston.demo.domain.ai.dto.internal.AssemblyAiTranscriptResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClovaSpeechService {

    @Value("${ai.clova.secret-key}")
    private String secretKey;

    @Value("${ai.clova.invoke-url}")
    private String invokeUrl;

    private final ObjectMapper objectMapper;

    public AssemblyAiTranscriptResult transcribe(String audioUrl) {
        WebClient client = WebClient.builder()
                .baseUrl(invokeUrl)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("X-CLOVASPEECH-API-KEY", secretKey)
                .defaultHeader("Content-Type", "application/json")
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("url", audioUrl);
        body.put("language", "ko-KR");
        body.put("completion", "sync");

        ObjectNode diarization = body.putObject("diarization");
        diarization.put("enable", true);
        diarization.put("speakerCountMin", 1);
        diarization.put("speakerCountMax", 5);

        JsonNode response;
        try {
            response = client.post()
                    .uri("/recognizer/url")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofMinutes(30));
        } catch (WebClientResponseException e) {
            throw new RuntimeException("[ClovaSTT] 전사 요청 실패: HTTP " + e.getStatusCode().value()
                    + " body=" + e.getResponseBodyAsString(), e);
        }

        if (response == null) {
            throw new RuntimeException("[ClovaSTT] 응답이 null입니다");
        }

        String result = response.path("result").asText("");
        if (!"COMPLETED".equalsIgnoreCase(result)) {
            throw new RuntimeException("[ClovaSTT] 전사 실패: result=" + result
                    + " message=" + response.path("message").asText());
        }

        return parseResult(response);
    }

    private AssemblyAiTranscriptResult parseResult(JsonNode response) {
        String fullText = response.path("text").asText("");
        JsonNode segments = response.path("segments");

        if (segments.isMissingNode() || !segments.isArray() || segments.isEmpty()) {
            log.warn("[ClovaSTT] segments 없음 — 단일 발화로 처리");
            return new AssemblyAiTranscriptResult(fullText,
                    fullText.isBlank() ? List.of()
                            : List.of(new AssemblyAiTranscriptResult.Utterance("SPEAKER_00", fullText, 0.0, 0.0)));
        }

        // 클로바 speaker label("1","2",...) → 정렬 후 SPEAKER_00, SPEAKER_01, ... 매핑
        List<String> orderedLabels = new ArrayList<>();
        for (JsonNode seg : segments) {
            String label = extractSpeakerLabel(seg);
            if (!orderedLabels.contains(label)) {
                orderedLabels.add(label);
            }
        }
        Collections.sort(orderedLabels);
        Map<String, String> labelMap = new LinkedHashMap<>();
        for (int i = 0; i < orderedLabels.size(); i++) {
            labelMap.put(orderedLabels.get(i), String.format("SPEAKER_%02d", i));
        }

        // 동일 화자 연속 세그먼트 병합
        List<AssemblyAiTranscriptResult.Utterance> utterances = new ArrayList<>();
        String prevSpeaker = null;
        StringBuilder segText = new StringBuilder();
        long segStart = 0, segEnd = 0;

        for (JsonNode seg : segments) {
            String rawLabel = extractSpeakerLabel(seg);
            String speaker = labelMap.getOrDefault(rawLabel, "SPEAKER_00");
            String text = seg.path("text").asText("").trim();
            long start = seg.path("start").asLong();
            long end = seg.path("end").asLong();

            if (text.isBlank()) continue;

            if (speaker.equals(prevSpeaker)) {
                segText.append(" ").append(text);
                segEnd = end;
            } else {
                if (prevSpeaker != null) {
                    utterances.add(new AssemblyAiTranscriptResult.Utterance(
                            prevSpeaker,
                            segText.toString().trim(),
                            segStart / 1000.0,
                            segEnd / 1000.0
                    ));
                }
                prevSpeaker = speaker;
                segText = new StringBuilder(text);
                segStart = start;
                segEnd = end;
            }
        }
        if (prevSpeaker != null && !segText.toString().isBlank()) {
            utterances.add(new AssemblyAiTranscriptResult.Utterance(
                    prevSpeaker,
                    segText.toString().trim(),
                    segStart / 1000.0,
                    segEnd / 1000.0
            ));
        }

        long distinctSpeakers = utterances.stream()
                .map(AssemblyAiTranscriptResult.Utterance::getSpeaker)
                .distinct().count();
        log.info("[ClovaSTT] 파싱 완료. utterances={}, distinctSpeakers={}", utterances.size(), distinctSpeakers);

        return new AssemblyAiTranscriptResult(fullText, utterances);
    }

    private String extractSpeakerLabel(JsonNode seg) {
        // diarization.label 우선, 없으면 speaker.label
        JsonNode diar = seg.path("diarization").path("label");
        if (!diar.isMissingNode() && !diar.isNull()) {
            return diar.asText("0");
        }
        return seg.path("speaker").path("label").asText("0");
    }
}
