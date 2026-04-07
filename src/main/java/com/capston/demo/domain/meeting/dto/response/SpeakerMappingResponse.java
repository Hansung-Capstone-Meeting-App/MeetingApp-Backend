package com.capston.demo.domain.meeting.dto.response;

import com.capston.demo.domain.meeting.entity.SpeakerMapping;
import lombok.Getter;

/**
 * 화자 매핑 응답 DTO
 * 엔티티: SpeakerMapping (speaker_mappings 테이블)
 * STT 화자 레이블과 실제 유저의 매핑 결과를 반환
 */
@Getter
public class SpeakerMappingResponse {

    /** speaker_mappings.id */
    private final Long id;

    /** speaker_mappings.transcript_id - 어떤 트랜스크립트의 매핑인지 */
    private final Long transcriptId;

    /** speaker_mappings.speaker_label - STT 화자 레이블 예) "SPEAKER_01" */
    private final String speakerLabel;

    /** speaker_mappings.user_name - 화자 이름 */
    private final String userName;

    /** speaker_mappings.user_id - 매핑된 실제 유저 ID (메신저 기능 추가 후 채워짐) */
    private final Long userId;

    public SpeakerMappingResponse(SpeakerMapping mapping) {
        this.id = mapping.getId();
        this.transcriptId = mapping.getTranscript().getId();
        this.speakerLabel = mapping.getSpeakerLabel();
        this.userName = mapping.getUserName();
        this.userId = mapping.getUserId();
    }
}
