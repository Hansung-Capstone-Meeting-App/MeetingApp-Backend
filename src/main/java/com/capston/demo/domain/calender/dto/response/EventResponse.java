package com.capston.demo.domain.calender.dto.response;

import com.capston.demo.domain.calender.entity.Event;
import com.capston.demo.domain.calender.entity.EventParticipant;
import com.capston.demo.domain.calender.entity.ParticipantStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public class EventResponse {

    private final Long id;
    private final Long workspaceId;
    private final Long meetingId;
    private final String title;
    private final String description;
    private final String location;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final Boolean isAllDay;
    private final Long createdBy;
    private final String color;
    private final LocalDateTime createdAt;
    private final List<ParticipantResponse> participants;

    public EventResponse(Event event) {
        this.id = event.getId();
        this.workspaceId = event.getWorkspaceId();
        this.meetingId = event.getMeetingId();
        this.title = event.getTitle();
        this.description = event.getDescription();
        this.location = event.getLocation();
        this.startAt = event.getStartAt();
        this.endAt = event.getEndAt();
        this.isAllDay = event.getIsAllDay();
        this.createdBy = event.getCreatedBy();
        this.color = event.getColor();
        this.createdAt = event.getCreatedAt();
        this.participants = event.getParticipants().stream()
                .map(ParticipantResponse::new)
                .collect(Collectors.toList());
    }

    @Getter
    public static class ParticipantResponse {
        private final Long userId;
        private final ParticipantStatus status;

        public ParticipantResponse(EventParticipant participant) {
            this.userId = participant.getUserId();
            this.status = participant.getStatus();
        }
    }
}
