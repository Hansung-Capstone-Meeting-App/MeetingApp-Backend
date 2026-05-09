package com.capston.demo.domain.calender.service;

import com.capston.demo.domain.calender.dto.request.EventCreateRequest;
import com.capston.demo.domain.calender.dto.request.EventUpdateRequest;
import com.capston.demo.domain.calender.dto.response.EventResponse;
import com.capston.demo.domain.calender.entity.Event;
import com.capston.demo.domain.calender.entity.EventParticipant;
import com.capston.demo.domain.calender.entity.ParticipantStatus;
import com.capston.demo.domain.calender.entity.Task;
import com.capston.demo.domain.calender.repository.EventRepository;
import com.capston.demo.domain.calender.repository.TaskRepository;
import com.capston.demo.domain.user.repository.WorkspaceMemberRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional(readOnly = true)
    public List<EventResponse> getEvents(Long meetingId, Long workspaceId, Long currentUserId) {
        if (meetingId != null) {
            List<Task> tasks = taskRepository.findByMeetingId(meetingId);
            return eventRepository.findByMeetingId(meetingId).stream()
                    .map(e -> new EventResponse(e, tasks))
                    .collect(Collectors.toList());
        }
        if (workspaceId != null) {
            List<Task> allTasks = taskRepository.findByWorkspaceId(workspaceId);
            Map<Long, List<Task>> tasksByMeetingId = allTasks.stream()
                    .filter(t -> t.getMeetingId() != null)
                    .collect(Collectors.groupingBy(Task::getMeetingId));
            return eventRepository.findByWorkspaceId(workspaceId).stream()
                    .map(e -> new EventResponse(e,
                            e.getMeetingId() != null
                                    ? tasksByMeetingId.getOrDefault(e.getMeetingId(), Collections.emptyList())
                                    : Collections.emptyList()))
                    .collect(Collectors.toList());
        }
        throw new IllegalArgumentException("meetingId 또는 workspaceId 중 하나는 필요합니다.");
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long id, Long userId) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        checkAccess(event.getWorkspaceId(), event.getCreatedBy(), userId);
        List<Task> tasks = event.getMeetingId() != null
                ? taskRepository.findByMeetingId(event.getMeetingId())
                : Collections.emptyList();
        return new EventResponse(event, tasks);
    }

    @Transactional
    public EventResponse createEvent(EventCreateRequest request, Long userId) {
        if (request.getWorkspaceId() != null &&
                !workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(request.getWorkspaceId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        Event event = new Event();
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setLocation(request.getLocation());
        event.setStartAt(request.getStartAt());
        event.setEndAt(request.getEndAt());
        event.setIsAllDay(Boolean.TRUE.equals(request.getIsAllDay()));
        event.setWorkspaceId(request.getWorkspaceId());
        event.setMeetingId(request.getMeetingId());
        event.setCreatedBy(userId);
        event.setColor(request.getColor() != null ? request.getColor() : "blue");

        if (request.getParticipantUserIds() != null) {
            for (Long participantId : request.getParticipantUserIds()) {
                EventParticipant participant = new EventParticipant();
                participant.setEvent(event);
                participant.setUserId(participantId);
                participant.setStatus(ParticipantStatus.PENDING);
                event.getParticipants().add(participant);
            }
        }

        Event saved = eventRepository.save(event);
        return new EventResponse(saved, Collections.emptyList());
    }

    @Transactional
    public EventResponse updateEvent(Long id, EventUpdateRequest request, Long userId) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        checkAccess(event.getWorkspaceId(), event.getCreatedBy(), userId);

        if (request.getTitle() != null)       event.setTitle(request.getTitle());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getLocation() != null)    event.setLocation(request.getLocation());
        if (request.getStartAt() != null)     event.setStartAt(request.getStartAt());
        if (request.getEndAt() != null)       event.setEndAt(request.getEndAt());
        if (request.getIsAllDay() != null)    event.setIsAllDay(request.getIsAllDay());
        if (request.getColor() != null)       event.setColor(request.getColor());

        List<Task> tasks = event.getMeetingId() != null
                ? taskRepository.findByMeetingId(event.getMeetingId())
                : Collections.emptyList();
        return new EventResponse(eventRepository.save(event), tasks);
    }

    @Transactional
    public void deleteEvent(Long id, Long userId) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        checkAccess(event.getWorkspaceId(), event.getCreatedBy(), userId);
        eventRepository.delete(event);
    }

    private void checkAccess(Long workspaceId, Long createdBy, Long userId) {
        if (workspaceId != null) {
            if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, userId)) {
                throw new BusinessException(ErrorCode.EVENT_ACCESS_DENIED);
            }
        } else if (createdBy != null && !createdBy.equals(userId)) {
            throw new BusinessException(ErrorCode.EVENT_ACCESS_DENIED);
        }
    }
}
