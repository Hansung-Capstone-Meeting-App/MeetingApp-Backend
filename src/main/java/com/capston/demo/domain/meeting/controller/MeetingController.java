package com.capston.demo.domain.meeting.controller;

import com.capston.demo.domain.meeting.controllerDocs.MeetingControllerDocs;
import com.capston.demo.domain.meeting.dto.request.MeetingRequest;
import com.capston.demo.domain.meeting.dto.request.SpeakerMappingRequest;
import com.capston.demo.domain.meeting.dto.request.TranscriptRequest;
import com.capston.demo.domain.meeting.dto.response.MeetingResponse;
import com.capston.demo.domain.meeting.dto.response.SpeakerMappingResponse;
import com.capston.demo.domain.meeting.dto.response.TranscriptResponse;
import com.capston.demo.domain.meeting.service.MeetingService;
import com.capston.demo.domain.meeting.service.MeetingTranscriptService;
import com.capston.demo.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController implements MeetingControllerDocs {

    private final MeetingService meetingService;
    private final MeetingTranscriptService transcriptService;

    // ── 회의 ──────────────────────────────────────────────────────────────────

    // 회의 시작 (application/json, { workspaceId, channelId, title })
    // POST /api/meetings
    @PostMapping
    public ResponseEntity<MeetingResponse> startMeeting(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody MeetingRequest request) {
        return ResponseEntity.ok(meetingService.startMeeting(request, userDetails.getUserId()));
    }

    // 회의 종료 (endedAt 기록)
    // PATCH /api/meetings/{id}/end
    @PatchMapping("/{id}/end")
    public ResponseEntity<MeetingResponse> endMeeting(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(meetingService.endMeeting(id, userDetails.getUserId()));
    }

    // 회의 단건 조회
    // GET /api/meetings/{id}
    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse> getMeeting(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(meetingService.getMeeting(id, userDetails.getUserId()));
    }

    // 회의 목록 조회 (workspaceId, channelId 중 하나 이상 필터 가능)
    // GET /api/meetings?workspaceId=1&channelId=2
    @GetMapping
    public ResponseEntity<List<MeetingResponse>> getMeetings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) Long channelId) {
        return ResponseEntity.ok(meetingService.getMeetings(workspaceId, channelId, userDetails.getUserId()));
    }

    // 회의 삭제
    // DELETE /api/meetings/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeeting(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        meetingService.deleteMeeting(id, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    // ── 트랜스크립트 ───────────────────────────────────────────────────────────

    // STT 결과(트랜스크립트) 저장
    // POST /api/meetings/{meetingId}/transcript
    @PostMapping("/{meetingId}/transcript")
    public ResponseEntity<TranscriptResponse> saveTranscript(
            @PathVariable Long meetingId,
            @RequestBody TranscriptRequest request) {
        return ResponseEntity.ok(transcriptService.saveTranscript(meetingId, request));
    }

    // 트랜스크립트 조회
    // GET /api/meetings/{meetingId}/transcript
    @GetMapping("/{meetingId}/transcript")
    public ResponseEntity<TranscriptResponse> getTranscript(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long meetingId) {
        return ResponseEntity.ok(transcriptService.getTranscript(meetingId, userDetails.getUserId()));
    }

    // ── 화자 매핑 ─────────────────────────────────────────────────────────────

    // 화자 레이블을 실제 참여자로 매핑 (저장/덮어쓰기)
    // PUT /api/meetings/transcripts/{transcriptId}/speaker-mappings
    @PutMapping("/transcripts/{transcriptId}/speaker-mappings")
    public ResponseEntity<List<SpeakerMappingResponse>> saveSpeakerMappings(
            @PathVariable String transcriptId,
            @RequestBody SpeakerMappingRequest request) {
        return ResponseEntity.ok(transcriptService.saveSpeakerMappings(transcriptId, request));
    }

    // 화자 매핑 목록 조회
    // GET /api/meetings/transcripts/{transcriptId}/speaker-mappings
    @GetMapping("/transcripts/{transcriptId}/speaker-mappings")
    public ResponseEntity<List<SpeakerMappingResponse>> getSpeakerMappings(@PathVariable String transcriptId) {
        return ResponseEntity.ok(transcriptService.getSpeakerMappings(transcriptId));
    }
}
