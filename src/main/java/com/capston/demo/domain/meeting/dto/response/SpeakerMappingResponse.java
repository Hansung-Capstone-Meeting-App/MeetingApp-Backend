package com.capston.demo.domain.meeting.dto.response;

import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import lombok.Getter;

@Getter
public class SpeakerMappingResponse {

    private final String transcriptId;
    private final String speakerLabel;
    private final String userName;
    private final Long userId;

    public SpeakerMappingResponse(String transcriptId, MeetingTranscript.SpeakerMappingEmbedded mapping) {
        this.transcriptId = transcriptId;
        this.speakerLabel = mapping.getSpeakerLabel();
        this.userName = mapping.getUserName();
        this.userId = mapping.getUserId();
    }
}
