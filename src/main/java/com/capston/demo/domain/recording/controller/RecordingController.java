package com.capston.demo.domain.recording.controller;

import com.capston.demo.domain.meeting.entity.RecordingStatus;
import com.capston.demo.domain.recording.dto.request.PresignedUploadRequest;
import com.capston.demo.domain.recording.dto.response.PresignedUrlResponse;
import com.capston.demo.domain.recording.dto.response.RecordingResponse;
import com.capston.demo.domain.recording.service.RecordingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/recordings")
@RequiredArgsConstructor
public class RecordingController {

    private final RecordingService recordingService;

    // POST /api/recordings/upload?meetingId=1
    @PostMapping("/upload")
    public ResponseEntity<RecordingResponse> upload(
            @RequestParam Long meetingId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(recordingService.upload(meetingId, file));
    }

    // GET /api/recordings?meetingId=1
    @GetMapping
    public ResponseEntity<List<RecordingResponse>> getRecordingsByMeeting(@RequestParam Long meetingId) {
        return ResponseEntity.ok(recordingService.getRecordingsByMeeting(meetingId));
    }

    // PATCH /api/recordings/{recordingId}/status?status=DONE
    @PatchMapping("/{recordingId}/status")
    public ResponseEntity<RecordingResponse> updateStatus(
            @PathVariable Long recordingId,
            @RequestParam RecordingStatus status) {
        return ResponseEntity.ok(recordingService.updateStatus(recordingId, status));
    }

    // POST /api/recordings/presigned-upload-url
    @PostMapping("/presigned-upload-url")
    public ResponseEntity<PresignedUrlResponse> getUploadPresignedUrl(
            @RequestBody PresignedUploadRequest request) {
        return ResponseEntity.ok(recordingService.generateUploadPresignedUrl(request));
    }

    // GET /api/recordings/{recordingId}/presigned-url
    @GetMapping("/{recordingId}/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getDownloadPresignedUrl(@PathVariable Long recordingId) {
        return ResponseEntity.ok(recordingService.generateDownloadPresignedUrl(recordingId));
    }

    // DELETE /api/recordings/{recordingId}
    @DeleteMapping("/{recordingId}")
    public ResponseEntity<Void> deleteRecording(@PathVariable Long recordingId) {
        recordingService.deleteRecording(recordingId);
        return ResponseEntity.noContent().build();
    }
}
