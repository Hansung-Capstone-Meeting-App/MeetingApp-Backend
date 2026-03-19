package com.capston.demo.domain.calender.service;

import com.capston.demo.domain.calender.dto.response.TaskResponse;
import com.capston.demo.domain.calender.repository.TaskRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasks(Long meetingId, Long workspaceId, Long assigneeId, Long createdBy) {
        if (meetingId != null) {
            return taskRepository.findByMeetingIdAndCreatedBy(meetingId, createdBy).stream()
                    .map(TaskResponse::new)
                    .collect(Collectors.toList());
        }

        if (workspaceId != null) {
            return taskRepository.findByWorkspaceIdAndCreatedBy(workspaceId, createdBy).stream()
                    .map(TaskResponse::new)
                    .collect(Collectors.toList());
        }

        if (assigneeId != null) {
            return taskRepository.findByAssigneeId(assigneeId).stream()
                    .map(TaskResponse::new)
                    .collect(Collectors.toList());
        }

        throw new IllegalArgumentException("meetingId, workspaceId, assigneeId 중 하나는 필요합니다.");
    }
}
