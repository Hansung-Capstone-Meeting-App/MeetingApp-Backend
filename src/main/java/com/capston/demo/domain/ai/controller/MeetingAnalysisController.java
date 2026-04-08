package com.capston.demo.domain.ai.controller;

import com.capston.demo.domain.ai.dto.response.GeminiAnalyzeResponse;
import com.capston.demo.domain.ai.dto.response.TranscribeResponse;
import com.capston.demo.domain.ai.service.MeetingAnalysisService;
import com.capston.demo.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingAnalysisController {

    private final MeetingAnalysisService analysisService;

    // 1단계: STT 전사
    // POST /api/meetings/{meetingId}/recordings/{recordingId}/transcribe
    @PostMapping("/{meetingId}/recordings/{recordingId}/transcribe")
    public ResponseEntity<TranscribeResponse> transcribe(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long meetingId,
            @PathVariable Long recordingId) {
        return ResponseEntity.ok(analysisService.transcribe(meetingId, recordingId, userDetails.getUserId()));
    }

    // 3단계: Gemini 분석 (화자 매핑 완료 후 호출)
    // POST /api/meetings/transcripts/{transcriptId}/gemini-analyze
    @PostMapping("/transcripts/{transcriptId}/gemini-analyze")
    public ResponseEntity<GeminiAnalyzeResponse> geminiAnalyze(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String transcriptId) {
        return ResponseEntity.ok(analysisService.geminiAnalyze(transcriptId, userDetails.getUserId()));
    }
}
