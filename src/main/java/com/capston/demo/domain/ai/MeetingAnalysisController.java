package com.capston.demo.domain.ai;

import com.capston.demo.domain.ai.dto.GeminiAnalysisResult;
import com.capston.demo.domain.ai.dto.MeetingAnalysisRequest;
import com.capston.demo.domain.ai.service.MeetingAnalysisService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingAnalysisController {
    private final MeetingAnalysisService analysisService;

    @PostMapping("/{meetingId}/analyze")
    public ResponseEntity<String> analyze(@PathVariable Long meetingId, @RequestBody MeetingAnalysisRequest request) {
        List<GeminiAnalysisResult.SpeakerInfo> speakerInfos = request.getSpeakerMappings().stream()
                .map(mapping -> new GeminiAnalysisResult.SpeakerInfo(
                        mapping.getSpeakerLabel(),
                        mapping.getUserId(),
                        mapping.getUserName()))
                .collect(Collectors.toList());

        analysisService.analyze(meetingId, request.getRecordingId(), speakerInfos);

        return ResponseEntity.ok("회의 분석이 완료되었습니다. 화자별 발화, 요약, 키워드, 일정이 저장되었습니다.");
    }
}
