package com.capston.demo.domain.recording.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Presigned URL 응답 DTO
 * S3 업로드(PUT)용 또는 다운로드/재생(GET)용 서명된 URL을 반환
 */
@Getter
@AllArgsConstructor
public class PresignedUrlResponse {

    /** AWS S3 서명된 URL (유효시간 1시간) */
    private final String presignedUrl;

    /** meeting_recordings.s3_key - 업로드 완료 후 MeetingRecording DB 등록 시 사용 */
    private final String s3Key;

    /** presignedUrl 만료 시각 */
    private final LocalDateTime expiresAt;
}
