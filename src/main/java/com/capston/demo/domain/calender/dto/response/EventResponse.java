package com.capston.demo.domain.calender.dto.response;

import com.capston.demo.domain.calender.entity.Event;
import com.capston.demo.domain.calender.entity.EventParticipant;
import com.capston.demo.domain.calender.entity.ParticipantStatus;
import com.capston.demo.domain.calender.entity.Task;
import com.capston.demo.domain.calender.entity.TaskStatus;
import java.time.LocalDateTime;
import java.util.Collections;
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
    private final String createdByName;
    private final String color;
    private final LocalDateTime createdAt;
    private final List<ParticipantResponse> participants;
    private final List<TaskSummary> relatedTasks;

    public EventResponse(Event event, List<Task> relatedTasks) {
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
        this.createdByName = event.getCreatedByName();
        this.color = event.getColor();
        this.createdAt = event.getCreatedAt();
        this.participants = event.getParticipants().stream()
                .map(ParticipantResponse::new)
                .collect(Collectors.toList());
        this.relatedTasks = relatedTasks == null ? Collections.emptyList() : relatedTasks.stream()
                .map(TaskSummary::new)
                .collect(Collectors.toList());
    }

    public EventResponse(Event event) {
        this(event, Collections.emptyList());
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

    @Getter
    public static class TaskSummary {
        private final Long id;
        private final String title;
        private final TaskStatus status;
        private final String assigneeName;
        private final LocalDateTime dueDate;

        public TaskSummary(Task task) {
            this.id = task.getId();
            this.title = task.getTitle();
            this.status = task.getStatus();
            this.assigneeName = task.getAssigneeName();
            this.dueDate = task.getDueDate();
        }
    }
}
