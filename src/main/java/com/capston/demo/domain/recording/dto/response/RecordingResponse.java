package com.capston.demo.domain.recording.dto.response;

import com.capston.demo.domain.meeting.entity.MeetingRecording;
import com.capston.demo.domain.meeting.entity.RecordingStatus;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 녹음 파일 응답 DTO
 * 엔티티: MeetingRecording (meeting_recordings 테이블)
 */
@Getter
public class RecordingResponse {

    /** meeting_recordings.id */
    private final Long recordingId;

    /** meeting_recordings.meeting_id - 어떤 회의의 녹음인지 */
    private final Long meetingId;

    /** meeting_recordings.s3_bucket - 파일이 저장된 S3 버킷명 */
    private final String s3Bucket;

    /** meeting_recordings.s3_key - S3 객체 키 예) "recordings/1/uuid.m4a" (Python MCP 서버에 전달) */
    private final String s3Key;

    /** meeting_recordings.file_size - 파일 크기 (bytes) */
    private final Long fileSize;

    /** meeting_recordings.duration_sec - 녹음 길이 (초), 업로드 직후에는 null */
    private final Integer durationSec;

    /** meeting_recordings.status - 처리 상태 (UPLOADING→UPLOADED→PROCESSING→DONE/FAILED) */
    private final RecordingStatus status;

    /** meeting_recordings.created_at - 레코드 생성 시각 */
    private final LocalDateTime createdAt;

    public RecordingResponse(MeetingRecording recording) {
        this.recordingId = recording.getId();
        this.meetingId = recording.getMeeting().getId();
        this.s3Bucket = recording.getS3Bucket();
        this.s3Key = recording.getS3Key();
        this.fileSize = recording.getFileSize();
        this.durationSec = recording.getDurationSec();
        this.status = recording.getStatus();
        this.createdAt = recording.getCreatedAt();
    }
}
