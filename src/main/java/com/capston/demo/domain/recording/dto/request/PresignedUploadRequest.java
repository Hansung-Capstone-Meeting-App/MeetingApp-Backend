package com.capston.demo.domain.recording.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Presigned PUT URL 발급 요청 DTO
 * 엔티티: MeetingRecording (업로드 완료 후 별도 등록)
 * 클라이언트가 S3에 직접 업로드할 때 사용하는 서명된 URL을 요청
 */
@Getter
@Setter
public class PresignedUploadRequest {

    /** meetings.id - 어떤 회의의 녹음인지 */
    private Long meetingId;

    /** S3 key 생성에 사용할 원본 파일명 (확장자 포함) 예) "회의녹음.m4a" */
    private String filename;
}
