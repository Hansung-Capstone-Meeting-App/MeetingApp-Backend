package com.capston.demo.domain.ai.dto;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class MeetingAnalysisRequest {
    private Long recordingId;
    private List<SpeakerMapping> speakerMappings;

    @Getter @Setter
    public static class SpeakerMapping {
        private String speakerLabel;
        private Long userId;
        private String userName;
    }
}
