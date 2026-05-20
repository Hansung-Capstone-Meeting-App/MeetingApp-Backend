package com.capston.demo.domain.ai.service;

import com.capston.demo.domain.ai.dto.internal.AssemblyAiTranscriptResult;
import com.capston.demo.domain.ai.dto.internal.GeminiAnalysisResult;
import com.capston.demo.domain.ai.dto.internal.GeminiCorrectionResult;
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
import java.util.stream.Collectors;
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

    public GeminiCorrectionResult correctTranscript(
            AssemblyAiTranscriptResult transcript,
            String meetingCategory,
            String meetingContext,
            String workspaceName,
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
        parts.addObject().put("text", buildCorrectionPrompt(
                transcript,
                meetingCategory,
                meetingContext,
                workspaceName,
                meetingTitle
        ));

        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0.0);

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
            throw new RuntimeException("[Gemini] correction request failed: HTTP " + e.getStatusCode().value()
                    + " body=" + e.getResponseBodyAsString(), e);
        }

        if (response == null) {
            throw new RuntimeException("[Gemini] correction response is null");
        }

        String text = response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
        if (text == null || text.isBlank()) {
            throw new RuntimeException("[Gemini] empty correction response body: " + response);
        }

        return parseCorrectionResponse(text, transcript);
    }

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

    private String buildCorrectionPrompt(
            AssemblyAiTranscriptResult transcript,
            String meetingCategory,
            String meetingContext,
            String workspaceName,
            String meetingTitle
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음은 AssemblyAI STT로 생성된 한국어 회의 전사문이다.\n");
        sb.append("목표는 문맥 기반 전사 오류 교정이다. 철자, 띄어쓰기, 조사, 문장부호, 명확한 STT 오인식 단어만 교정하라.\n");
        sb.append("회의에서 말하지 않은 새 정보는 추가하지 말고, 의미가 불확실한 단어는 원문을 유지하라.\n");
        sb.append("워크스페이스명: ").append(nullToUnknown(workspaceName)).append("\n");
        sb.append("회의 제목: ").append(nullToUnknown(meetingTitle)).append("\n");
        sb.append("사용자 선택 카테고리: ").append(nullToUnknown(meetingCategory)).append("\n");
        sb.append("사용자 추가 문맥: ").append(nullToUnknown(meetingContext)).append("\n");
        sb.append("카테고리가 기타, OTHER, 없음, null, 빈 값이면 워크스페이스명, 회의 제목, 전사문 전체에서 주제를 먼저 추론하고 그 주제에 맞게 교정하라.\n");
        sb.append("카테고리가 명확하면 해당 도메인의 용어를 우선 고려하되, 문맥상 확실한 경우에만 바꿔라.\n");
        sb.append("예: 협업/문서/개발 문맥에서 '노천'이 나오면 '노션'으로 교정할 수 있다.\n");
        sb.append("예: 개발 문맥에서는 MongoDB, MySQL, Slack, S3, API, GitHub, Figma, Notion 같은 용어를 고려하라.\n");
        sb.append("예: 의류/패션 문맥에서는 원단, 패턴, 봉제, 샘플, 핏, 사이즈, 컬러웨이, 룩북 같은 용어를 고려하라.\n");
        sb.append("단, 실제 일반 명사일 수 있거나 확신이 낮으면 강제 치환하지 말고 원문을 유지하라.\n");
        sb.append("speakerLabel, 발화 순서, 시작/종료 시간은 원본과 동일하게 유지하라.\n");
        sb.append("utterances 항목 수와 순서는 입력과 반드시 같아야 한다.\n");
        sb.append("correctedText에는 분석에 사용할 깨끗한 교정문만 넣고, 괄호 표시는 넣지 마라.\n");
        sb.append("displayText에는 사용자에게 보여줄 문장을 넣어라. 교정된 단어는 '교정어(원문: 원래단어)' 형식으로 표시하라.\n");
        sb.append("corrections에는 실제로 바꾼 단어만 넣고, 띄어쓰기/문장부호만 바뀐 경우는 넣지 않아도 된다.\n");
        sb.append("응답은 유효한 JSON만 반환하라. 설명 문장, 마크다운 코드블록, 추가 텍스트는 포함하지 마라.\n\n");
        sb.append("입력 전사문:\n");

        int index = 0;
        for (AssemblyAiTranscriptResult.Utterance utterance : transcript.getUtterances()) {
            sb.append(String.format(
                    Locale.ROOT,
                    "%d. speakerLabel=%s, startSec=%.3f, endSec=%.3f, text=%s%n",
                    index++,
                    utterance.getSpeaker(),
                    utterance.getStartSec(),
                    utterance.getEndSec(),
                    utterance.getText()
            ));
        }

        sb.append("\n아래 스키마에 맞는 JSON만 반환하라.\n");
        sb.append("{\"correctedFullText\":\"string\",");
        sb.append("\"displayFullText\":\"string\",");
        sb.append("\"utterances\":[{\"speakerLabel\":\"string\",\"originalText\":\"string\",");
        sb.append("\"correctedText\":\"string\",\"displayText\":\"string\",");
        sb.append("\"corrections\":[{\"original\":\"string\",\"corrected\":\"string\",\"reason\":\"string\"}],");
        sb.append("\"startSec\":0.0,\"endSec\":0.0}]}");
        return sb.toString();
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
        sb.append("고유명사와 기술명사를 제외하고 영어 문장으로 쓰지 마라.\n");
        sb.append("회의 제목: ").append(meetingTitle == null ? "제목 없음" : meetingTitle).append("\n");
        sb.append("회의 기준 날짜: ").append(meetingDate.format(DATE_FORMATTER)).append("\n");
        sb.append("회의록의 '오늘', '내일', '다음 주', '월요일', '화요일' 같은 상대 날짜 표현은 반드시 회의 기준 날짜를 기준으로 해석하라.\n");
        sb.append("tasks와 events는 회의에서 실제로 하기로 정했거나 마감 목표가 언급된 내용만 추출하라.\n");
        sb.append("불확실한 농담이나 단순 의견은 제외하되, '올릴게요', '배포할게요', '추가해둘게요', '완료할게요' 같은 실행 약속은 포함하라.\n");
        sb.append("아래 참여자 목록에서 speakerLabel과 회의록의 화자 라벨이 일치한다.\n");
        sb.append("tasks의 담당자가 명확하면 해당 화자의 userName을 assigneeName에 넣고, 불명확하면 null을 넣어라.\n");
        sb.append("events의 createdByName은 해당 일정을 제안하거나 주도한 화자의 userName을 넣고, 불명확하면 null을 넣어라.\n");
        sb.append("keywords는 3~8개만 간결하게 반환하라.\n");
        sb.append("summary는 2~4문장으로 작성하라.\n");
        sb.append("tasks.dueDate는 마감 시점이 명확할 때만 yyyy-MM-dd'T'HH:mm:ss 형식으로 반환하고, 불명확하면 null로 반환하라.\n");
        sb.append("events는 회의, 발표, 배포, 제출, 마감, 미팅, 데모, 구현 완료 목표처럼 캘린더에 올릴 만한 항목을 추출하라.\n");
        sb.append("events.startAt과 events.endAt은 반드시 yyyy-MM-dd'T'HH:mm:ss 형식으로 반환하라.\n");
        sb.append("시간은 없고 날짜만 명확하면 isAllDay=true로 두고 startAt은 00:00:00, endAt은 23:59:59로 반환하라.\n");
        sb.append("날짜나 시간이 너무 불명확해서 캘린더에 넣기 곤란하면 events에 넣지 마라.\n");
        sb.append("events.participantUserIds에는 참석자나 해당자가 명확히 언급된 userId만 넣고, 없으면 빈 배열 []로 반환하라.\n");
        sb.append("description은 한두 문장으로 간단히 작성하라.\n\n");
        sb.append("참여자:\n");

        for (GeminiAnalysisResult.SpeakerInfo info : speakerInfos) {
            sb.append(String.format(
                    Locale.ROOT,
                    "- speakerLabel=%s, userName=%s%n",
                    info.getSpeakerLabel(),
                    info.getUserName()
            ));
        }

        sb.append("\n회의록:\n");
        for (AssemblyAiTranscriptResult.Utterance utterance : transcript.getUtterances()) {
            sb.append(String.format(Locale.ROOT, "[%s] %s%n", utterance.getSpeaker(), utterance.getText()));
        }

        sb.append("\n아래 스키마에 맞는 유효한 JSON만 반환하라.\n");
        sb.append("설명 문장, 마크다운 코드블록, 추가 텍스트는 절대 포함하지 마라.\n");
        sb.append("{\"summary\":\"string\",\"keywords\":[\"string\"],");
        sb.append("\"tasks\":[{\"speakerLabel\":\"string\",\"assigneeName\":null,\"title\":\"string\",\"description\":\"string\",\"dueDate\":null}],");
        sb.append("\"events\":[{\"speakerLabel\":\"string\",\"userId\":null,\"createdByName\":null,\"participantUserIds\":[],\"title\":\"string\",\"description\":\"string\",\"location\":null,\"startAt\":\"yyyy-MM-dd'T'HH:mm:ss\",\"endAt\":\"yyyy-MM-dd'T'HH:mm:ss\",\"isAllDay\":false}]}");
        return sb.toString();
    }

    private GeminiAnalysisResult parseResponse(String text) {
        try {
            String json = unwrapJson(text);
            JsonNode root = objectMapper.readTree(json.trim());
            String summary = root.path("summary").asText("");

            List<String> keywords = new ArrayList<>();
            root.path("keywords").forEach(keyword -> keywords.add(keyword.asText()));

            List<GeminiAnalysisResult.ExtractedTask> tasks = new ArrayList<>();
            root.path("tasks").forEach(task -> tasks.add(new GeminiAnalysisResult.ExtractedTask(
                    task.path("speakerLabel").asText(),
                    task.path("assigneeName").isNull() ? null : task.path("assigneeName").asText(),
                    task.path("title").asText(),
                    task.path("description").asText(""),
                    task.path("dueDate").isNull() ? null : task.path("dueDate").asText()
            )));

            List<GeminiAnalysisResult.ExtractedEvent> events = new ArrayList<>();
            root.path("events").forEach(event -> events.add(new GeminiAnalysisResult.ExtractedEvent(
                    event.path("speakerLabel").asText(),
                    event.path("userId").isNull() ? null : event.path("userId").asLong(),
                    event.path("createdByName").isNull() ? null : event.path("createdByName").asText(),
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

    private GeminiCorrectionResult parseCorrectionResponse(String text, AssemblyAiTranscriptResult original) {
        try {
            String json = unwrapJson(text);
            JsonNode root = objectMapper.readTree(json.trim());
            List<GeminiCorrectionResult.CorrectedUtterance> utterances = new ArrayList<>();
            JsonNode returnedUtterances = root.path("utterances");

            for (int i = 0; i < original.getUtterances().size(); i++) {
                AssemblyAiTranscriptResult.Utterance source = original.getUtterances().get(i);
                JsonNode corrected = returnedUtterances.isArray() && returnedUtterances.size() > i
                        ? returnedUtterances.get(i)
                        : null;

                String correctedText = corrected == null
                        ? source.getText()
                        : corrected.path("correctedText").asText(source.getText());
                String normalizedCorrectedText = normalizeCorrectionText(correctedText, source.getText());
                List<GeminiCorrectionResult.CorrectionItem> corrections = parseCorrectionItems(
                        corrected == null ? null : corrected.path("corrections")
                );
                String displayText = corrected == null
                        ? normalizedCorrectedText
                        : normalizeCorrectionText(corrected.path("displayText").asText(normalizedCorrectedText), normalizedCorrectedText);

                utterances.add(new GeminiCorrectionResult.CorrectedUtterance(
                        source.getSpeaker(),
                        source.getText(),
                        normalizedCorrectedText,
                        displayText,
                        corrections,
                        source.getStartSec(),
                        source.getEndSec()
                ));
            }

            String correctedFullText = root.path("correctedFullText").asText("");
            if (correctedFullText.isBlank()) {
                correctedFullText = buildCorrectedFullText(utterances);
            }

            String displayFullText = root.path("displayFullText").asText("");
            if (displayFullText.isBlank()) {
                displayFullText = buildDisplayFullText(utterances);
            }

            return new GeminiCorrectionResult(correctedFullText, displayFullText, utterances);
        } catch (Exception e) {
            throw new RuntimeException("[Gemini] correction parse failed: " + e.getMessage(), e);
        }
    }

    private String unwrapJson(String text) {
        String json = text.trim();
        if (json.contains("```json")) {
            json = json.substring(json.indexOf("```json") + 7, json.lastIndexOf("```"));
        } else if (json.contains("```")) {
            json = json.substring(json.indexOf("```") + 3, json.lastIndexOf("```"));
        }
        return json;
    }

    private List<GeminiCorrectionResult.CorrectionItem> parseCorrectionItems(JsonNode node) {
        List<GeminiCorrectionResult.CorrectionItem> corrections = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return corrections;
        }

        node.forEach(item -> {
            String original = normalizeText(item.path("original").asText(null));
            String corrected = normalizeText(item.path("corrected").asText(null));
            if (original == null || corrected == null || original.equals(corrected)) {
                return;
            }
            corrections.add(new GeminiCorrectionResult.CorrectionItem(
                    original,
                    corrected,
                    normalizeText(item.path("reason").asText(null))
            ));
        });
        return corrections;
    }

    private String normalizeCorrectionText(String correctedText, String fallback) {
        if (correctedText == null || correctedText.isBlank()) {
            return fallback;
        }
        return correctedText.trim().replaceAll("\\s+", " ");
    }

    private String buildCorrectedFullText(List<GeminiCorrectionResult.CorrectedUtterance> utterances) {
        return utterances.stream()
                .map(GeminiCorrectionResult.CorrectedUtterance::getCorrectedText)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String buildDisplayFullText(List<GeminiCorrectionResult.CorrectedUtterance> utterances) {
        return utterances.stream()
                .map(GeminiCorrectionResult.CorrectedUtterance::getDisplayText)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
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

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private String nullToUnknown(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? "없음" : normalized;
    }
}
