package com.capston.demo.domain.recording.dto.response;

import com.capston.demo.domain.meeting.entity.MeetingRecording;
import com.capston.demo.domain.meeting.entity.RecordingStatus;
import lombok.Getter;

@Getter
public class RecordingStatusResponse {
    private final Long recordingId;
    private final Long meetingId;
    private final RecordingStatus status;

    public RecordingStatusResponse(MeetingRecording recording) {
        this.recordingId = recording.getId();
        this.meetingId = recording.getMeeting().getId();
        this.status = recording.getStatus();
    }
}

