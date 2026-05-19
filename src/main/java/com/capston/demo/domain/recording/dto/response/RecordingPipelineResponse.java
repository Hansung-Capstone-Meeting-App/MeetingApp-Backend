package com.capston.demo.domain.recording.dto.response;

import com.capston.demo.domain.meeting.entity.MeetingRecording;
import com.capston.demo.domain.meeting.entity.RecordingStatus;
import lombok.Getter;

@Getter
public class RecordingPipelineResponse {

    private final Long recordingId;
    private final Long meetingId;
    private final RecordingStatus recordingStatus;
    private final RecordingPipelinePhase phase;
    private final String transcriptId;
    private final boolean mappingComplete;
    private final boolean analysisComplete;
    private final String errorMessage;

    public RecordingPipelineResponse(
            MeetingRecording recording,
            RecordingPipelinePhase phase,
            String transcriptId,
            boolean mappingComplete,
            boolean analysisComplete,
            String errorMessage) {
        this.recordingId = recording.getId();
        this.meetingId = recording.getMeeting().getId();
        this.recordingStatus = recording.getStatus();
        this.phase = phase;
        this.transcriptId = transcriptId;
        this.mappingComplete = mappingComplete;
        this.analysisComplete = analysisComplete;
        this.errorMessage = errorMessage;
    }
}
