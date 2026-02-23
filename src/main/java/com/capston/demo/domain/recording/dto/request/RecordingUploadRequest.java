package com.capston.demo.domain.recording.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 서버 경유 녹음 파일 업로드 요청 DTO
 * 엔티티: MeetingRecording
 * 파일은 multipart/form-data로 별도 전달, 여기선 meetingId만 받음
 */
@Getter
@Setter
public class RecordingUploadRequest {

    /** meeting_recordings.meeting_id - 어떤 회의의 녹음인지 (meetings.id 참조) */
    private Long meetingId;
}
