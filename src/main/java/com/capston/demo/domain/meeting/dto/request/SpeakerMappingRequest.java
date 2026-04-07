package com.capston.demo.domain.meeting.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 화자 매핑 저장 요청 DTO
 * 엔티티: SpeakerMapping
 * STT 화자 레이블(SPEAKER_01 등)을 실제 유저와 연결할 때 사용
 */
@Getter
@Setter
public class SpeakerMappingRequest {

    /** 여러 화자를 한 번에 매핑하는 목록 */
    private List<MappingItem> mappings;

    /**
     * 단일 화자 매핑 항목
     * 엔티티: SpeakerMapping
     */
    @Getter
    @Setter
    public static class MappingItem {

        /** speaker_mappings.speaker_label - STT가 부여한 화자 레이블 예) "SPEAKER_01" */
        private String speakerLabel;

        /** speaker_mappings.user_name - 화자 이름 (현재는 직접 입력, 추후 톡방 멤버 선택으로 대체) */
        private String userName;

        /** speaker_mappings.user_id - 매핑할 실제 유저 ID (메신저 기능 추가 후 사용) */
        private Long userId;
    }
}
