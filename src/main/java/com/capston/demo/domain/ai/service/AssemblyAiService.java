package com.capston.demo.domain.ai.service;

import com.capston.demo.domain.ai.dto.AssemblyAiTranscriptResult;
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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssemblyAiService {
    @Value("${ai.assemblyai.api-key}")
    private String apiKey;

    @Value("${ai.assemblyai.base-url}")
    private String baseUrl;

    private final ObjectMapper objectMapper;

    public AssemblyAiTranscriptResult transcribe(String audioUrl) {
        WebClient client = WebClient.builder().baseUrl(baseUrl)
                .defaultHeader("Authorization", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        String id = submitTranscript(client, audioUrl);
        JsonNode result = pollUntilCompleted(client, id);
        return parseResult(result);
    }

    private String submitTranscript(WebClient client, String audioUrl) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("audio_url", audioUrl);
        body.put("speaker_labels", true);
        body.put("language_code", "ko");
        body.putArray("speech_models").add("universal-2");

        JsonNode res;
        try {
            res = client.post().uri("/v2/transcript").bodyValue(body)
                    .retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(30));
        } catch (WebClientResponseException e) {
            throw new RuntimeException("[AssemblyAI] submit failed: HTTP " + e.getStatusCode().value()
                    + " body=" + e.getResponseBodyAsString(), e);
        }

        if (res == null || !res.has("id")) {
            throw new RuntimeException("[AssemblyAI] submit failed: missing transcript id");
        }

        return res.get("id").asText();
    }

    private JsonNode pollUntilCompleted(WebClient client, String id) {
        for (int i = 0; i < 120; i++) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            JsonNode res = client.get().uri("/v2/transcript/" + id)
                    .retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(30));

            if (res == null) {
                continue;
            }

            String status = res.path("status").asText();
            if ("completed".equals(status)) {
                return res;
            }
            if ("error".equals(status)) {
                throw new RuntimeException("[AssemblyAI] transcription error: " + res.path("error").asText());
            }
        }

        throw new RuntimeException("[AssemblyAI] transcription timeout");
    }

    private AssemblyAiTranscriptResult parseResult(JsonNode result) {
        String fullText = result.path("text").asText("");
        List<AssemblyAiTranscriptResult.Utterance> utterances = new ArrayList<>();

        for (JsonNode u : result.path("utterances")) {
            utterances.add(new AssemblyAiTranscriptResult.Utterance(
                    u.path("speaker").asText(),
                    u.path("text").asText(),
                    u.path("start").asLong() / 1000.0,
                    u.path("end").asLong() / 1000.0
            ));
        }

        return new AssemblyAiTranscriptResult(fullText, utterances);
    }
}
