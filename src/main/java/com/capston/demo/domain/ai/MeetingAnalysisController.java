package com.capston.demo.domain.ai;

import com.capston.demo.domain.ai.dto.ClaudeAnalysisResult;
import com.capston.demo.domain.ai.dto.MeetingAnalysisRequest;
import com.capston.demo.domain.ai.service.MeetingAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingAnalysisController {
    private final MeetingAnalysisService analysisService;

    @PostMapping("/{meetingId}/analyze")
    public ResponseEntity<String> analyze(@PathVariable Long meetingId, @RequestBody MeetingAnalysisRequest request) {
        List<ClaudeAnalysisResult.SpeakerInfo> speakerInfos = request.getSpeakerMappings().stream()
                .map(m -> new ClaudeAnalysisResult.SpeakerInfo(m.getSpeakerLabel(), m.getUserId(), m.getUserName()))
                .collect(Collectors.toList());
        analysisService.analyze(meetingId, request.getRecordingId(), speakerInfos);
        return ResponseEntity.ok("회의 분석이 완료되었습니다. Task와 Event가 생성되었습니다.");
    }
}
