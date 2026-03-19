package com.capston.demo.domain.meeting.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 회의 생성 요청 DTO
 * 엔티티: Meeting
 */
@Getter
@Setter
public class MeetingRequest {

    /** meetings.workspace_id - 어느 워크스페이스에서 열린 회의인지 */
    private Long workspaceId;

    /** meetings.channel_id - 어느 채널에서 열린 회의인지 */
    private Long channelId;

    /** meetings.title - 회의 제목 */
    private String title;

}
