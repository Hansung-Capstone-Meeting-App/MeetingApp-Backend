package com.capston.demo.domain.meeting.controller;

import com.capston.demo.domain.meeting.controllerDocs.MeetingControllerDocs;
import com.capston.demo.domain.meeting.dto.request.MeetingRequest;
import com.capston.demo.domain.meeting.dto.request.SpeakerMappingRequest;
import com.capston.demo.domain.meeting.dto.request.TranscriptRequest;
import com.capston.demo.domain.meeting.dto.response.MeetingNotionExportResponse;
import com.capston.demo.domain.meeting.dto.response.MeetingResponse;
import com.capston.demo.domain.meeting.dto.response.MeetingSummaryResponse;
import com.capston.demo.domain.meeting.dto.response.SpeakerMappingResponse;
import com.capston.demo.domain.meeting.dto.response.TranscriptResponse;
import com.capston.demo.domain.meeting.service.MeetingExportService;
import com.capston.demo.domain.meeting.service.MeetingService;
import com.capston.demo.domain.meeting.service.MeetingTranscriptService;
import com.capston.demo.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController implements MeetingControllerDocs {

    private final MeetingService meetingService;
    private final MeetingTranscriptService transcriptService;
    private final MeetingExportService meetingExportService;

    // 회의 생성 (워크스페이스 소속)
    // POST /api/meetings
    @PostMapping
    public ResponseEntity<MeetingResponse> createMeeting(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody MeetingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.createMeeting(request, userDetails.getUserId()));
    }

    // 회의 단건 조회
    // GET /api/meetings/{id}
    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse> getMeeting(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(meetingService.getMeeting(id, userDetails.getUserId()));
    }

    // 회의 목록 조회
    // GET /api/meetings?workspaceId=1  → 워크스페이스 소속 회의
    // GET /api/meetings                → 내가 생성한 회의 (Slack용)
    @GetMapping
    public ResponseEntity<List<MeetingResponse>> getMeetings(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long workspaceId) {
        Long userId = userDetails.getUserId();
        if (workspaceId != null) {
            return ResponseEntity.ok(meetingService.getMeetingsByWorkspace(workspaceId, userId));
        }
        return ResponseEntity.ok(meetingService.getMeetings(userId));
    }

    // 회의 대시보드 요약
    // GET /api/meetings/{id}/summary
    @GetMapping("/{id}/summary")
    public ResponseEntity<MeetingSummaryResponse> getMeetingSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(meetingService.getMeetingSummary(id, userDetails.getUserId()));
    }

    /**
     * 회의 리포트 PDF보내기
     * GET /api/meetings/{id}/export/pdf?includeEvents=true
     *
     * - JWT 인증 필수, 회의 접근 권한은 MeetingExportService에서 검증
     * - 응답: application/pdf 바이너리 + Content-Disposition(다운로드 파일명)
     * - includeEvents=false 이면 PDF에서 일정 섹션만 제외 (요약·할일은 항상 포함)
     */
    @GetMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportMeetingPdf(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean includeEvents) {
        MeetingExportService.ExportResult result = meetingExportService.exportPdf(id, userDetails.getUserId(), includeEvents); // 회의 리포트 PDF 생성

        ContentDisposition disposition = ContentDisposition.attachment() // 다운로드 파일명
                .filename(result.fileName(), StandardCharsets.UTF_8)
                .build(); // 한글 파일명 지원 (UTF-8)
        return ResponseEntity.ok() // 회의 리포트 PDF 바이너리 반환
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()) // 다운로드 파일명
                .contentType(MediaType.APPLICATION_PDF) // application/pdf
                .body(result.pdfBytes()); // 회의 리포트 PDF 바이너리 반환
    }

    /**
     * 회의 리포트를 Notion 회의록 DB에보내기
     * POST /api/meetings/{id}/notion-export?includeEvents=true
     */
    @PostMapping("/{id}/notion-export")
    public ResponseEntity<MeetingNotionExportResponse> exportMeetingToNotion(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean includeEvents) {
        return ResponseEntity.ok(
                meetingExportService.exportToNotion(id, userDetails.getUserId(), includeEvents));
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
