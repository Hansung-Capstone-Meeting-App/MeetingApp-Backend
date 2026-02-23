package com.capston.demo.domain.meeting.controller;

import com.capston.demo.domain.meeting.dto.request.MeetingRequest;
import com.capston.demo.domain.meeting.dto.request.SpeakerMappingRequest;
import com.capston.demo.domain.meeting.dto.request.TranscriptRequest;
import com.capston.demo.domain.meeting.dto.response.MeetingResponse;
import com.capston.demo.domain.meeting.dto.response.SpeakerMappingResponse;
import com.capston.demo.domain.meeting.dto.response.TranscriptResponse;
import com.capston.demo.domain.meeting.service.MeetingService;
import com.capston.demo.domain.meeting.service.MeetingTranscriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final MeetingTranscriptService transcriptService;

    // ── 회의 ──────────────────────────────────────────────────────────────────

    // POST /api/meetings
    @PostMapping
    public ResponseEntity<MeetingResponse> startMeeting(@RequestBody MeetingRequest request) {
        return ResponseEntity.ok(meetingService.startMeeting(request));
    }

    // PATCH /api/meetings/{id}/end
    @PatchMapping("/{id}/end")
    public ResponseEntity<MeetingResponse> endMeeting(@PathVariable Long id) {
        return ResponseEntity.ok(meetingService.endMeeting(id));
    }

    // GET /api/meetings/{id}
    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse> getMeeting(@PathVariable Long id) {
        return ResponseEntity.ok(meetingService.getMeeting(id));
    }

    // GET /api/meetings?workspaceId=1&channelId=2
    @GetMapping
    public ResponseEntity<List<MeetingResponse>> getMeetings(
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) Long channelId) {
        return ResponseEntity.ok(meetingService.getMeetings(workspaceId, channelId));
    }

    // DELETE /api/meetings/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeeting(@PathVariable Long id) {
        meetingService.deleteMeeting(id);
        return ResponseEntity.noContent().build();
    }

    // ── 트랜스크립트 ───────────────────────────────────────────────────────────

    // POST /api/meetings/{meetingId}/transcript
    @PostMapping("/{meetingId}/transcript")
    public ResponseEntity<TranscriptResponse> saveTranscript(
            @PathVariable Long meetingId,
            @RequestBody TranscriptRequest request) {
        return ResponseEntity.ok(transcriptService.saveTranscript(meetingId, request));
    }

    // GET /api/meetings/{meetingId}/transcript
    @GetMapping("/{meetingId}/transcript")
    public ResponseEntity<TranscriptResponse> getTranscript(@PathVariable Long meetingId) {
        return ResponseEntity.ok(transcriptService.getTranscript(meetingId));
    }

    // ── 화자 매핑 ─────────────────────────────────────────────────────────────

    // PUT /api/meetings/transcripts/{transcriptId}/speaker-mappings
    @PutMapping("/transcripts/{transcriptId}/speaker-mappings")
    public ResponseEntity<List<SpeakerMappingResponse>> saveSpeakerMappings(
            @PathVariable Long transcriptId,
            @RequestBody SpeakerMappingRequest request) {
        return ResponseEntity.ok(transcriptService.saveSpeakerMappings(transcriptId, request));
    }

    // GET /api/meetings/transcripts/{transcriptId}/speaker-mappings
    @GetMapping("/transcripts/{transcriptId}/speaker-mappings")
    public ResponseEntity<List<SpeakerMappingResponse>> getSpeakerMappings(@PathVariable Long transcriptId) {
        return ResponseEntity.ok(transcriptService.getSpeakerMappings(transcriptId));
    }
}
