package com.capston.demo.domain.recording.service;

import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.entity.MeetingRecording;
import com.capston.demo.domain.meeting.entity.RecordingStatus;
import com.capston.demo.domain.meeting.repository.MeetingRecordingRepository;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.recording.dto.request.PresignedUploadRequest;
import com.capston.demo.domain.recording.dto.response.PresignedUrlResponse;
import com.capston.demo.domain.recording.dto.response.RecordingResponse;
import com.capston.demo.global.util.S3Util;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecordingService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Util s3Util;
    private final MeetingRepository meetingRepository;
    private final MeetingRecordingRepository recordingRepository;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private static final Duration PRESIGNED_EXPIRY = Duration.ofHours(1);

    // ── 서버 경유 업로드 ────────────────────────────────────────────────────────

    @Transactional
    public RecordingResponse upload(Long meetingId, MultipartFile file) throws IOException {
        s3Util.validateAudioFile(file);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId));

        String s3Key = s3Util.generateKey(meetingId, file.getOriginalFilename());

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .contentType(file.getContentType())
                        .contentLength(file.getSize())
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );

        MeetingRecording recording = new MeetingRecording();
        recording.setMeeting(meeting);
        recording.setS3Bucket(bucket);
        recording.setS3Key(s3Key);
        recording.setFileSize(file.getSize());
        recording.setStatus(RecordingStatus.UPLOADED);

        return new RecordingResponse(recordingRepository.save(recording));
    }

    // ── 목록 조회 ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RecordingResponse> getRecordingsByMeeting(Long meetingId) {
        return recordingRepository.findByMeetingId(meetingId).stream()
                .map(RecordingResponse::new)
                .collect(Collectors.toList());
    }

    // ── 상태 업데이트 (STT 서버에서 호출) ─────────────────────────────────────

    @Transactional
    public RecordingResponse updateStatus(Long recordingId, RecordingStatus status) {
        MeetingRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("녹음 파일을 찾을 수 없습니다. id=" + recordingId));
        recording.setStatus(status);
        return new RecordingResponse(recording);
    }

    // ── Presigned PUT URL (클라이언트 직접 S3 업로드용) ─────────────────────────

    public PresignedUrlResponse generateUploadPresignedUrl(PresignedUploadRequest request) {
        meetingRepository.findById(request.getMeetingId())
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + request.getMeetingId()));

        String s3Key = s3Util.generateKey(request.getMeetingId(), request.getFilename());

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(PRESIGNED_EXPIRY)
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(s3Key)
                                .build())
                        .build()
        );

        return new PresignedUrlResponse(presigned.url().toString(), s3Key, LocalDateTime.now().plusHours(1));
    }

    // ── Presigned GET URL (다운로드/재생용) ────────────────────────────────────

    public PresignedUrlResponse generateDownloadPresignedUrl(Long recordingId) {
        MeetingRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("녹음 파일을 찾을 수 없습니다. id=" + recordingId));

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(PRESIGNED_EXPIRY)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(recording.getS3Bucket())
                                .key(recording.getS3Key())
                                .build())
                        .build()
        );

        return new PresignedUrlResponse(presigned.url().toString(), recording.getS3Key(), LocalDateTime.now().plusHours(1));
    }

    // ── 삭제 ──────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteRecording(Long recordingId) {
        MeetingRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("녹음 파일을 찾을 수 없습니다. id=" + recordingId));

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(recording.getS3Bucket())
                .key(recording.getS3Key())
                .build());

        recordingRepository.delete(recording);
    }
}
