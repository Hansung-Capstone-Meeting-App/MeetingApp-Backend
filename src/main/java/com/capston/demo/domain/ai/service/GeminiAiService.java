package com.capston.demo.domain.ai.service;

import com.capston.demo.domain.ai.dto.AssemblyAiTranscriptResult;
import com.capston.demo.domain.ai.dto.GeminiAnalysisResult;
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
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiAiService {
    @Value("${ai.gemini.api-key}")
    private String apiKey;

    @Value("${ai.gemini.base-url}")
    private String baseUrl;

    @Value("${ai.gemini.model}")
    private String model;

    private final ObjectMapper objectMapper;

    public GeminiAnalysisResult analyze(AssemblyAiTranscriptResult transcript, List<GeminiAnalysisResult.SpeakerInfo> speakerInfos) {
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

    private String buildPrompt(AssemblyAiTranscriptResult transcript, List<GeminiAnalysisResult.SpeakerInfo> speakerInfos) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 한국어 회의록입니다. 회의 내용을 분석해서 요약, 키워드, 할 일을 추출하세요.\n");
        sb.append("응답의 모든 자연어 텍스트는 반드시 한국어로 작성하세요.\n");
        sb.append("영어 문장으로 요약하거나 영어 키워드를 만들지 마세요. 필요할 때만 고유명사나 기술명칭에 한해 영어를 유지하세요.\n");
        sb.append("일정(events)은 아직 미구현 기능이므로 항상 빈 배열 []로 반환하세요.\n");
        sb.append("회의에서 명확하게 확정된 할 일만 추출하세요. 단순 논의, 아이디어, 가능성, 불확실한 제안은 tasks에 넣지 마세요.\n");
        sb.append("화자와 담당자가 명확하면 userId를 넣고, 불명확하면 null로 반환하세요.\n");
        sb.append("dueDate는 회의 내용에서 마감 기한이 명확할 때만 yyyy-MM-dd'T'HH:mm:ss 형식으로 작성하고, 없으면 null로 반환하세요.\n");
        sb.append("keywords는 한국어 핵심어 3~8개만 간결하게 반환하세요.\n");
        sb.append("summary는 한국어 2~4문장으로 작성하세요.\n\n");
        sb.append("참여자:\n");

        for (GeminiAnalysisResult.SpeakerInfo info : speakerInfos) {
            sb.append(String.format(Locale.ROOT, "- speaker=%s, userId=%s, userName=%s%n",
                    info.getSpeakerLabel(), info.getUserId(), info.getUserName()));
        }

        sb.append("\n회의록:\n");
        for (AssemblyAiTranscriptResult.Utterance u : transcript.getUtterances()) {
            sb.append(String.format(Locale.ROOT, "[%s] %s%n", u.getSpeaker(), u.getText()));
        }

        sb.append("\n아래 스키마에 맞는 유효한 JSON만 반환하세요.\n");
        sb.append("설명 문장, 코드블록 마크다운, 추가 텍스트는 절대 포함하지 마세요.\n");
        sb.append("{\"summary\":\"string\",\"keywords\":[\"string\"],");
        sb.append("\"tasks\":[{\"speakerLabel\":\"string\",\"userId\":null,\"title\":\"string\",\"description\":\"string\",\"dueDate\":null}],");
        sb.append("\"events\":[]}");
        return sb.toString();
    }

    private GeminiAnalysisResult parseResponse(String text) {
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

            List<GeminiAnalysisResult.ExtractedTask> tasks = new ArrayList<>();
            root.path("tasks").forEach(t -> tasks.add(new GeminiAnalysisResult.ExtractedTask(
                    t.path("speakerLabel").asText(),
                    t.path("userId").isNull() ? null : t.path("userId").asLong(),
                    t.path("title").asText(),
                    t.path("description").asText(""),
                    t.path("dueDate").isNull() ? null : t.path("dueDate").asText())));

            List<GeminiAnalysisResult.ExtractedEvent> events = new ArrayList<>();
            root.path("events").forEach(e -> events.add(new GeminiAnalysisResult.ExtractedEvent(
                    e.path("speakerLabel").asText(),
                    e.path("userId").isNull() ? null : e.path("userId").asLong(),
                    e.path("title").asText(),
                    e.path("description").asText(""),
                    e.path("location").isNull() ? null : e.path("location").asText(),
                    e.path("startAt").isNull() ? null : e.path("startAt").asText(),
                    e.path("endAt").isNull() ? null : e.path("endAt").asText())));

            return new GeminiAnalysisResult(summary, keywords, tasks, events);
        } catch (Exception e) {
            throw new RuntimeException("[Gemini] parse failed: " + e.getMessage(), e);
        }
    }
}
