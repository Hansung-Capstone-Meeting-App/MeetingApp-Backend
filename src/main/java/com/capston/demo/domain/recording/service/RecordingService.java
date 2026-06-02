package com.capston.demo.domain.recording.service;

import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.entity.MeetingRecording;
import com.capston.demo.domain.meeting.entity.RecordingStatus;
import com.capston.demo.domain.meeting.repository.MeetingRecordingRepository;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.recording.dto.request.PresignedUploadRequest;
import com.capston.demo.domain.recording.dto.response.PresignedUrlResponse;
import com.capston.demo.domain.recording.dto.response.RecordingResponse;
import com.capston.demo.domain.recording.dto.response.RecordingStatusResponse;
import com.capston.demo.domain.user.repository.WorkspaceMemberRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import com.capston.demo.global.util.S3Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsCredentialsProvider awsCredentialsProvider;
    private final S3Util s3Util;
    private final MeetingRepository meetingRepository;
    private final MeetingRecordingRepository recordingRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.region.static}")
    private String defaultRegion;

    @Value("${cloud.aws.s3.endpoint:}")
    private String endpoint;

    private static final Duration PRESIGNED_EXPIRY = Duration.ofHours(1);

    // ── 서버 경유 업로드 ────────────────────────────────────────────────────────

    @Transactional
    public RecordingResponse upload(Long meetingId, Long userId, MultipartFile file) throws IOException {
        s3Util.validateAudioFile(file);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        checkAccess(meeting, userId);

        String s3Key = s3Util.generateKey(meetingId, file.getOriginalFilename());

        S3Client client = resolveClientForBucket(bucket);
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Key)
                            .contentType(file.getContentType())
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } finally {
            closeIfRegional(client);
        }

        MeetingRecording recording = new MeetingRecording();
        recording.setMeeting(meeting);
        recording.setS3Bucket(bucket);
        recording.setS3Key(s3Key);
        recording.setFileSize(file.getSize());
        recording.setStatus(RecordingStatus.UPLOADED);

        return new RecordingResponse(recordingRepository.save(recording));
    }

    // ── Slack 등 내부에서 InputStream으로 직접 업로드 ─────────────────────────

    @Transactional
    public RecordingResponse uploadFromStream(Long meetingId, java.io.InputStream inputStream,
                                              long fileSize, String filename) throws IOException {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));

        String s3Key = s3Util.generateKey(meetingId, filename);

        S3Client client = resolveClientForBucket(bucket);
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Key)
                            .contentLength(fileSize)
                            .build(),
                    RequestBody.fromInputStream(inputStream, fileSize)
            );
        } finally {
            closeIfRegional(client);
        }

        MeetingRecording recording = new MeetingRecording();
        recording.setMeeting(meeting);
        recording.setS3Bucket(bucket);
        recording.setS3Key(s3Key);
        recording.setFileSize(fileSize);
        recording.setStatus(RecordingStatus.UPLOADED);

        return new RecordingResponse(recordingRepository.save(recording));
    }

    // ── 목록 조회 ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RecordingResponse> getRecordingsByMeeting(Long meetingId, Long userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        checkAccess(meeting, userId);
        return recordingRepository.findByMeetingId(meetingId).stream()
                .map(RecordingResponse::new)
                .collect(Collectors.toList());
    }

    // ── 상태 조회 (프론트 폴링용) ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RecordingStatusResponse getRecordingStatus(Long recordingId, Long userId) {
        MeetingRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORDING_NOT_FOUND));
        checkAccess(recording.getMeeting(), userId);
        return new RecordingStatusResponse(recording);
    }

    // ── 상태 업데이트 (STT 서버에서 호출) ─────────────────────────────────────

    @Transactional
    public RecordingResponse updateStatus(Long recordingId, RecordingStatus status) {
        MeetingRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORDING_NOT_FOUND));
        recording.setStatus(status);
        return new RecordingResponse(recording);
    }

    // ── Presigned PUT URL (클라이언트 직접 S3 업로드용) ─────────────────────────

    public PresignedUrlResponse generateUploadPresignedUrl(PresignedUploadRequest request, Long userId) {
        Meeting meeting = meetingRepository.findById(request.getMeetingId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        checkAccess(meeting, userId);

        String s3Key = s3Util.generateKey(request.getMeetingId(), request.getFilename());

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_EXPIRY)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .build())
                .build();

        PresignedPutObjectRequest presigned;
        S3Presigner presigner = resolvePresignerForBucket(bucket);
        try {
            presigned = presigner.presignPutObject(presignRequest);
        } finally {
            closeIfRegional(presigner);
        }

        return new PresignedUrlResponse(presigned.url().toString(), s3Key, LocalDateTime.now().plusHours(1));
    }

    // ── Presigned GET URL (다운로드/재생용) ────────────────────────────────────

    @Transactional(readOnly = true)
    public PresignedUrlResponse generateDownloadPresignedUrl(Long recordingId, Long userId) {
        MeetingRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORDING_NOT_FOUND));
        checkAccess(recording.getMeeting(), userId);

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_EXPIRY)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(recording.getS3Bucket())
                        .key(recording.getS3Key())
                        .build())
                .build();

        PresignedGetObjectRequest presigned;
        S3Presigner presigner = resolvePresignerForBucket(recording.getS3Bucket());
        try {
            presigned = presigner.presignGetObject(presignRequest);
        } finally {
            closeIfRegional(presigner);
        }

        return new PresignedUrlResponse(presigned.url().toString(), recording.getS3Key(), LocalDateTime.now().plusHours(1));
    }

    // ── 삭제 ──────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteRecording(Long recordingId, Long userId) {
        MeetingRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORDING_NOT_FOUND));
        checkAccess(recording.getMeeting(), userId);

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(recording.getS3Bucket())
                .key(recording.getS3Key())
                .build());

        recordingRepository.delete(recording);
    }

    // meeting.workspaceId 있으면 멤버십 체크, 없으면(Slack 생성) createdBy 체크
    private void checkAccess(Meeting meeting, Long userId) {
        if (meeting.getWorkspaceId() != null) {
            if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(meeting.getWorkspaceId(), userId)) {
                throw new BusinessException(ErrorCode.MEETING_ACCESS_DENIED);
            }
        } else {
            if (!userId.equals(meeting.getCreatedBy())) {
                throw new BusinessException(ErrorCode.MEETING_ACCESS_DENIED);
            }
        }
    }

    private S3Client resolveClientForBucket(String bucketName) {
        String region = resolveBucketRegion(bucketName);
        if (!StringUtils.hasText(region) || region.equals(defaultRegion)) {
            return s3Client;
        }
        log.warn("Using bucket region {} instead of configured region {}", region, defaultRegion);
        return buildRegionalClient(region);
    }

    private S3Presigner resolvePresignerForBucket(String bucketName) {
        String region = resolveBucketRegion(bucketName);
        if (!StringUtils.hasText(region) || region.equals(defaultRegion)) {
            return s3Presigner;
        }
        log.warn("Using bucket region {} instead of configured region {} for presign", region, defaultRegion);
        return buildRegionalPresigner(region);
    }

    private String resolveBucketRegion(String bucketName) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return defaultRegion;
        } catch (S3Exception e) {
            String bucketRegion = extractBucketRegion(e);
            if (StringUtils.hasText(bucketRegion)) {
                return bucketRegion;
            }
            throw e;
        }
    }

    private String extractBucketRegion(S3Exception e) {
        return Optional.ofNullable(e.awsErrorDetails())
                .flatMap(details -> details.sdkHttpResponse().firstMatchingHeader("x-amz-bucket-region"))
                .orElse(null);
    }

    private S3Client buildRegionalClient(String region) {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(awsCredentialsProvider)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(false)
                        .build());
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(buildRegionalEndpoint(region)));
        }
        return builder.build();
    }

    private S3Presigner buildRegionalPresigner(String region) {
        var builder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(awsCredentialsProvider);
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(buildRegionalEndpoint(region)));
        }
        return builder.build();
    }

    private String buildRegionalEndpoint(String region) {
        if (!StringUtils.hasText(endpoint)) {
            return "https://s3." + region + ".amazonaws.com";
        }
        return endpoint.replace(defaultRegion, region);
    }

    private void closeIfRegional(S3Client client) {
        if (client != s3Client) {
            client.close();
        }
    }

    private void closeIfRegional(S3Presigner presigner) {
        if (presigner != s3Presigner) {
            presigner.close();
        }
    }
}
