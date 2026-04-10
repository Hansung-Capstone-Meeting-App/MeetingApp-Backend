package com.capston.demo.domain.slack.controller;

import com.capston.demo.domain.slack.service.SlackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/slack")
@RequiredArgsConstructor
public class SlackInteractionController {

    private final ObjectMapper objectMapper;
    private final SlackService slackService;

    @PostMapping("/interactions")
    public ResponseEntity<?> handleInteraction(@RequestParam("payload") String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        String type = node.path("type").asText();

        if ("block_actions".equals(type)) {
            JsonNode action = node.path("actions").get(0);
            String actionId = action.path("action_id").asText();

            if ("open_speaker_mapping".equals(actionId)) {
                String triggerId = node.path("trigger_id").asText();
                String transcriptId = action.path("value").asText();
                log.info("화자 매핑 버튼 클릭. transcriptId={}", transcriptId);
                slackService.openSpeakerMappingModal(triggerId, transcriptId);
            }

        } else if ("view_submission".equals(type)) {
            JsonNode view = node.path("view");
            String callbackId = view.path("callback_id").asText();

            if ("speaker_mapping_modal".equals(callbackId)) {
                String transcriptId = view.path("private_metadata").asText();
                JsonNode values = view.path("state").path("values");
                log.info("화자 매핑 Modal 제출. transcriptId={}", transcriptId);
                slackService.handleSpeakerMappingSubmit(transcriptId, values);
            }
        }

        return ResponseEntity.ok().build();
    }
}
