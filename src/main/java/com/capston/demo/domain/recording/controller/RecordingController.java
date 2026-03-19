package com.capston.demo.domain.recording.controller;

import com.capston.demo.domain.meeting.entity.RecordingStatus;
import com.capston.demo.domain.recording.controllerDocs.RecordingControllerDocs;
import com.capston.demo.domain.recording.dto.request.PresignedUploadRequest;
import com.capston.demo.domain.recording.dto.response.PresignedUrlResponse;
import com.capston.demo.domain.recording.dto.response.RecordingResponse;
import com.capston.demo.domain.recording.service.RecordingService;
import com.capston.demo.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/recordings")
@RequiredArgsConstructor
public class RecordingController implements RecordingControllerDocs {

    private final RecordingService recordingService;

    // 음성 파일 서버 경유 업로드 (multipart/form-data, form-part: file)
    // POST /api/recordings/upload?meetingId=1
    @PostMapping("/upload")
    public ResponseEntity<RecordingResponse> upload(
            @RequestParam Long meetingId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(recordingService.upload(meetingId, file));
    }

    // 특정 회의의 녹음 목록 조회
    // GET /api/recordings?meetingId=1
    @GetMapping
    public ResponseEntity<List<RecordingResponse>> getRecordingsByMeeting(@RequestParam Long meetingId) {
        return ResponseEntity.ok(recordingService.getRecordingsByMeeting(meetingId));
    }

    // 녹음 처리 상태 변경 (STT 서버에서 호출)
    // PATCH /api/recordings/{recordingId}/status?status=DONE
    @PatchMapping("/{recordingId}/status")
    public ResponseEntity<RecordingResponse> updateStatus(
            @PathVariable Long recordingId,
            @RequestParam RecordingStatus status) {
        return ResponseEntity.ok(recordingService.updateStatus(recordingId, status));
    }

    // 클라이언트 직접 S3 업로드용 Presigned PUT URL 발급 (application/json, { meetingId, filename })
    // POST /api/recordings/presigned-upload-url
    @PostMapping("/presigned-upload-url")
    public ResponseEntity<PresignedUrlResponse> getUploadPresignedUrl(
            @RequestBody PresignedUploadRequest request) {
        return ResponseEntity.ok(recordingService.generateUploadPresignedUrl(request));
    }

    // 녹음 파일 재생/다운로드용 Presigned GET URL 발급 (유효시간 1시간)
    // GET /api/recordings/{recordingId}/presigned-url
    @GetMapping("/{recordingId}/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getDownloadPresignedUrl(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long recordingId) {
        return ResponseEntity.ok(recordingService.generateDownloadPresignedUrl(recordingId, userDetails.getUserId()));
    }

    // 녹음 파일 삭제 (S3 + DB 동시 삭제)
    // DELETE /api/recordings/{recordingId}
    @DeleteMapping("/{recordingId}")
    public ResponseEntity<Void> deleteRecording(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long recordingId) {
        recordingService.deleteRecording(recordingId, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }
}
