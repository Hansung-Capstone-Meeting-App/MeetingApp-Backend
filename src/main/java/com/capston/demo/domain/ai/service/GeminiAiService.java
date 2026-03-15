package com.capston.demo.domain.ai.service;

import com.capston.demo.domain.ai.dto.AssemblyAiTranscriptResult;
import com.capston.demo.domain.ai.dto.GeminiAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiAiService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Value("${ai.gemini.api-key}")
    private String apiKey;

    @Value("${ai.gemini.base-url}")
    private String baseUrl;

    @Value("${ai.gemini.model}")
    private String model;

    private final ObjectMapper objectMapper;

    public GeminiAnalysisResult analyze(
            AssemblyAiTranscriptResult transcript,
            List<GeminiAnalysisResult.SpeakerInfo> speakerInfos,
            LocalDate meetingDate,
            String meetingTitle
    ) {
        WebClient client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode userContent = contents.addObject();
        ArrayNode parts = userContent.putArray("parts");
        parts.addObject().put("text", buildPrompt(transcript, speakerInfos, meetingDate, meetingTitle));

        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.1);

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

    private String buildPrompt(
            AssemblyAiTranscriptResult transcript,
            List<GeminiAnalysisResult.SpeakerInfo> speakerInfos,
            LocalDate meetingDate,
            String meetingTitle
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 한국어 회의록이다. 회의 내용을 분석해서 요약, 키워드, 할 일, 일정을 추출하라.\n");
        sb.append("응답의 모든 자연어 텍스트는 반드시 한국어로 작성하라.\n");
        sb.append("고유명사와 기술명을 제외하고 영어 문장을 쓰지 마라.\n");
        sb.append("회의 제목: ").append(meetingTitle == null ? "제목 없음" : meetingTitle).append("\n");
        sb.append("회의 기준 날짜: ").append(meetingDate.format(DATE_FORMATTER)).append("\n");
        sb.append("회의록의 '오늘', '내일', '다음 주', '화요일', '수요일' 같은 상대 날짜 표현은 반드시 회의 기준 날짜를 기준으로 해석하라.\n");
        sb.append("tasks와 events는 회의에서 실제로 하기로 정했거나 마감 목표가 언급된 내용만 추출하라.\n");
        sb.append("불확실한 잡담이나 단순 의견은 제외하되, '올릴게요', '배포할게요', '추가해볼게요', '완료할게요' 같은 실행 약속은 포함하라.\n");
        sb.append("담당자가 명확하면 userId를 넣고, 불명확하면 null을 넣어라.\n");
        sb.append("keywords는 3~8개만 간결하게 반환하라.\n");
        sb.append("summary는 2~4문장으로 작성하라.\n");
        sb.append("tasks.dueDate는 마감 시점이 명확할 때만 yyyy-MM-dd'T'HH:mm:ss 형식으로 반환하고, 불명확하면 null로 반환하라.\n");
        sb.append("events는 회의, 발표, 배포, 제출, 마감, 미팅, 데모, 구현 완료 목표일처럼 캘린더에 올릴 만한 항목을 추출하라.\n");
        sb.append("기능 배포 예정일, 구현 완료 목표일, 제출 마감일은 event로 적극 추출하라.\n");
        sb.append("하나의 회의에서 여러 일정이 나오면 여러 개의 events를 반환하라. 한 개만 고르지 마라.\n");
        sb.append("날짜가 2개 이상 언급되더라도 각 일정이 별개의 실행 약속이면 각각 event로 분리하라.\n");
        sb.append("events.startAt과 events.endAt은 반드시 yyyy-MM-dd'T'HH:mm:ss 형식으로 반환하라.\n");
        sb.append("시간이 없고 날짜만 명확하면 isAllDay=true로 두고 startAt은 00:00:00, endAt은 23:59:59로 반환하라.\n");
        sb.append("날짜나 시간이 너무 불명확해서 캘린더에 넣기 곤란하면 events에 넣지 마라.\n");
        sb.append("events.participantUserIds에는 참석자나 담당자가 명확히 언급된 userId만 넣고, 없으면 빈 배열 []로 반환하라.\n");
        sb.append("description은 한두 문장으로 간단히 작성하라.\n\n");
        sb.append("참여자\n");

        for (GeminiAnalysisResult.SpeakerInfo info : speakerInfos) {
            sb.append(String.format(
                    Locale.ROOT,
                    "- speaker=%s, userId=%s, userName=%s%n",
                    info.getSpeakerLabel(),
                    info.getUserId(),
                    info.getUserName()
            ));
        }

        sb.append("\n회의록\n");
        for (AssemblyAiTranscriptResult.Utterance utterance : transcript.getUtterances()) {
            sb.append(String.format(Locale.ROOT, "[%s] %s%n", utterance.getSpeaker(), utterance.getText()));
        }

        sb.append("\n아래 스키마에 맞는 유효한 JSON만 반환하라.\n");
        sb.append("설명 문장, 마크다운 코드블록, 추가 텍스트는 절대 포함하지 마라.\n");
        sb.append("{\"summary\":\"string\",\"keywords\":[\"string\"],");
        sb.append("\"tasks\":[{\"speakerLabel\":\"string\",\"userId\":null,\"title\":\"string\",\"description\":\"string\",\"dueDate\":null}],");
        sb.append("\"events\":[{\"speakerLabel\":\"string\",\"userId\":null,\"participantUserIds\":[],\"title\":\"string\",\"description\":\"string\",\"location\":null,\"startAt\":\"yyyy-MM-dd'T'HH:mm:ss\",\"endAt\":\"yyyy-MM-dd'T'HH:mm:ss\",\"isAllDay\":false}]}");
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
            root.path("keywords").forEach(keyword -> keywords.add(keyword.asText()));

            List<GeminiAnalysisResult.ExtractedTask> tasks = new ArrayList<>();
            root.path("tasks").forEach(task -> tasks.add(new GeminiAnalysisResult.ExtractedTask(
                    task.path("speakerLabel").asText(),
                    task.path("userId").isNull() ? null : task.path("userId").asLong(),
                    task.path("title").asText(),
                    task.path("description").asText(""),
                    task.path("dueDate").isNull() ? null : task.path("dueDate").asText()
            )));

            List<GeminiAnalysisResult.ExtractedEvent> events = new ArrayList<>();
            root.path("events").forEach(event -> events.add(new GeminiAnalysisResult.ExtractedEvent(
                    event.path("speakerLabel").asText(),
                    event.path("userId").isNull() ? null : event.path("userId").asLong(),
                    parseParticipantUserIds(event.path("participantUserIds")),
                    event.path("title").asText(),
                    event.path("description").asText(""),
                    event.path("location").isNull() ? null : event.path("location").asText(),
                    normalizeDateTime(event.path("startAt").isNull() ? null : event.path("startAt").asText()),
                    normalizeDateTime(event.path("endAt").isNull() ? null : event.path("endAt").asText()),
                    event.path("isAllDay").isMissingNode() || event.path("isAllDay").isNull()
                            ? null
                            : event.path("isAllDay").asBoolean()
            )));

            return new GeminiAnalysisResult(summary, keywords, tasks, events);
        } catch (Exception e) {
            throw new RuntimeException("[Gemini] parse failed: " + e.getMessage(), e);
        }
    }

    private List<Long> parseParticipantUserIds(JsonNode node) {
        List<Long> participantUserIds = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return participantUserIds;
        }

        node.forEach(value -> {
            if (value != null && value.canConvertToLong()) {
                participantUserIds.add(value.asLong());
            }
        });
        return participantUserIds;
    }

    private String normalizeDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(value, DATE_TIME_FORMATTER).format(DATE_TIME_FORMATTER);
        } catch (Exception ignored) {
        }

        try {
            return LocalDate.parse(value).atStartOfDay().format(DATE_TIME_FORMATTER);
        } catch (Exception ignored) {
            return value;
        }
    }
}
