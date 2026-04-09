package com.capston.demo.domain.slack.controller;

import com.capston.demo.domain.slack.service.SlackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/slack")
@RequiredArgsConstructor
public class SlackEventController {

    private final ObjectMapper objectMapper;
    private final SlackService slackService;

    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleEvent(@RequestBody String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        String type = node.path("type").asText();

        // Slack URL 검증 challenge
        if ("url_verification".equals(type)) {
            String challenge = node.path("challenge").asText();
            return ResponseEntity.ok(Map.of("challenge", challenge));
        }

        if ("event_callback".equals(type)) {
            JsonNode event = node.path("event");
            String eventType = event.path("type").asText();

            if ("file_shared".equals(eventType)) {
                String fileId = event.path("file_id").asText();
                String userId = event.path("user_id").asText();
                log.info("file_shared 이벤트 수신. fileId={}, userId={}", fileId, userId);
                slackService.handleFileShared(fileId, userId);
            } else {
                log.info("처리하지 않는 이벤트 타입. type={}", eventType);
            }
        }

        return ResponseEntity.ok().build();
    }
}
