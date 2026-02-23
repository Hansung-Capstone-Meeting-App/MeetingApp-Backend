package com.capston.demo.domain.meeting.dto.response;

import com.capston.demo.domain.meeting.entity.Meeting;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 회의 응답 DTO
 * 엔티티: Meeting (meetings 테이블)
 */
@Getter
public class MeetingResponse {

    /** meetings.id */
    private final Long id;

    /** meetings.workspace_id - 워크스페이스 ID */
    private final Long workspaceId;

    /** meetings.channel_id - 채널 ID */
    private final Long channelId;

    /** meetings.title - 회의 제목 */
    private final String title;

    /** meetings.started_at - 회의 시작 시각 */
    private final LocalDateTime startedAt;

    /** meetings.ended_at - 회의 종료 시각 (종료 전이면 null) */
    private final LocalDateTime endedAt;

    /** meetings.created_by - 회의 생성자 유저 ID */
    private final Long createdBy;

    /** meetings.created_at - 레코드 생성 시각 */
    private final LocalDateTime createdAt;

    public MeetingResponse(Meeting meeting) {
        this.id = meeting.getId();
        this.workspaceId = meeting.getWorkspaceId();
        this.channelId = meeting.getChannelId();
        this.title = meeting.getTitle();
        this.startedAt = meeting.getStartedAt();
        this.endedAt = meeting.getEndedAt();
        this.createdBy = meeting.getCreatedBy();
        this.createdAt = meeting.getCreatedAt();
    }
}
