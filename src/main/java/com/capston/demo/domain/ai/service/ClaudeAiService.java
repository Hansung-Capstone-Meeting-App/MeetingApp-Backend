package com.capston.demo.domain.ai.service;

import com.capston.demo.domain.ai.dto.AssemblyAiTranscriptResult;
import com.capston.demo.domain.ai.dto.ClaudeAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
public class ClaudeAiService {
    @Value("${ai.gemini.api-key}")
    private String apiKey;

    @Value("${ai.gemini.base-url}")
    private String baseUrl;

    @Value("${ai.gemini.model}")
    private String model;

    private final ObjectMapper objectMapper;

    public ClaudeAnalysisResult analyze(AssemblyAiTranscriptResult transcript, List<ClaudeAnalysisResult.SpeakerInfo> speakerInfos) {
        WebClient client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode userContent = contents.addObject();
        ArrayNode parts = userContent.putArray("parts");
        parts.addObject().put("text", buildPrompt(transcript, speakerInfos));

        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.2);

        JsonNode response;
        try {
            response = client.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(90));
        } catch (WebClientResponseException e) {
            throw new RuntimeException("[Gemini] request failed: HTTP " + e.getStatusCode().value()
                    + " body=" + e.getResponseBodyAsString(), e);
        }

        if (response == null) {
            throw new RuntimeException("[Gemini] response is null");
        }

        String text = response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
        if (text == null || text.isBlank()) {
            throw new RuntimeException("[Gemini] empty response body: " + response);
        }

        return parseResponse(text);
    }

    private String buildPrompt(AssemblyAiTranscriptResult transcript, List<ClaudeAnalysisResult.SpeakerInfo> speakerInfos) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analyze the following meeting transcript and extract summary, keywords, tasks, and events.\n\n");
        sb.append("Participants:\n");

        for (ClaudeAnalysisResult.SpeakerInfo info : speakerInfos) {
            sb.append(String.format("- speaker=%s, userId=%d, userName=%s\n",
                    info.getSpeakerLabel(), info.getUserId(), info.getUserName()));
        }

        sb.append("\nTranscript:\n");
        for (AssemblyAiTranscriptResult.Utterance u : transcript.getUtterances()) {
            sb.append(String.format("[%s] %s\n", u.getSpeaker(), u.getText()));
        }

        sb.append("\nReturn ONLY valid JSON with this schema:\n");
        sb.append("{\"summary\":\"string\",\"keywords\":[\"string\"],");
        sb.append("\"tasks\":[{\"speakerLabel\":\"string\",\"userId\":1,\"title\":\"string\",\"description\":\"string\",\"dueDate\":\"yyyy-MM-dd'T'HH:mm:ss\"}],");
        sb.append("\"events\":[{\"speakerLabel\":\"string\",\"userId\":1,\"title\":\"string\",\"description\":\"string\",\"location\":null,\"startAt\":\"yyyy-MM-dd'T'HH:mm:ss\",\"endAt\":\"yyyy-MM-dd'T'HH:mm:ss\"}]}");
        return sb.toString();
    }

    private ClaudeAnalysisResult parseResponse(String text) {
        try {
            String json = text.trim();
            if (json.contains("```json")) {
                json = json.substring(json.indexOf("```json") + 7, json.lastIndexOf("```"));
            } else if (json.contains("```")) {
                json = json.substring(json.indexOf("```") + 3, json.lastIndexOf("```"));
            }

            JsonNode root = objectMapper.readTree(json.trim());
            String summary = root.path("summary").asText("");

            List<String> keywords = new ArrayList<>();
            root.path("keywords").forEach(k -> keywords.add(k.asText()));

            List<ClaudeAnalysisResult.ExtractedTask> tasks = new ArrayList<>();
            root.path("tasks").forEach(t -> tasks.add(new ClaudeAnalysisResult.ExtractedTask(
                    t.path("speakerLabel").asText(),
                    t.path("userId").asLong(),
                    t.path("title").asText(),
                    t.path("description").asText(""),
                    t.path("dueDate").isNull() ? null : t.path("dueDate").asText())));

            List<ClaudeAnalysisResult.ExtractedEvent> events = new ArrayList<>();
            root.path("events").forEach(e -> events.add(new ClaudeAnalysisResult.ExtractedEvent(
                    e.path("speakerLabel").asText(),
                    e.path("userId").asLong(),
                    e.path("title").asText(),
                    e.path("description").asText(""),
                    e.path("location").isNull() ? null : e.path("location").asText(),
                    e.path("startAt").isNull() ? null : e.path("startAt").asText(),
                    e.path("endAt").isNull() ? null : e.path("endAt").asText())));

            return new ClaudeAnalysisResult(summary, keywords, tasks, events);
        } catch (Exception e) {
            throw new RuntimeException("[Gemini] parse failed: " + e.getMessage(), e);
        }
    }
}