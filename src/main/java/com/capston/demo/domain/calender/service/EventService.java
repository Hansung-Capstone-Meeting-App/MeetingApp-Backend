package com.capston.demo.domain.calender.service;

import com.capston.demo.domain.calender.dto.response.EventResponse;
import com.capston.demo.domain.calender.repository.EventRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public List<EventResponse> getEvents(Long meetingId, Long workspaceId, Long createdBy) {
        if (meetingId != null) {
            return eventRepository.findByMeetingIdAndCreatedBy(meetingId, createdBy).stream()
                    .map(EventResponse::new)
                    .collect(Collectors.toList());
        }

        if (workspaceId != null) {
            return eventRepository.findByWorkspaceIdAndCreatedBy(workspaceId, createdBy).stream()
                    .map(EventResponse::new)
                    .collect(Collectors.toList());
        }

        throw new IllegalArgumentException("meetingId 또는 workspaceId 중 하나는 필요합니다.");
    }
}
