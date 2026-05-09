package com.capston.demo.domain.calender.service;

import com.capston.demo.domain.calender.dto.request.TaskCreateRequest;
import com.capston.demo.domain.calender.dto.request.TaskUpdateRequest;
import com.capston.demo.domain.calender.dto.response.TaskResponse;
import com.capston.demo.domain.calender.dto.response.TaskStatsResponse;
import com.capston.demo.domain.calender.entity.Task;
import com.capston.demo.domain.calender.entity.TaskSource;
import com.capston.demo.domain.calender.entity.TaskStatus;
import com.capston.demo.domain.calender.repository.TaskRepository;
import com.capston.demo.domain.user.repository.WorkspaceMemberRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasks(Long meetingId, Long workspaceId, Long assigneeId,
                                        TaskStatus status, LocalDateTime dueBefore, Long currentUserId) {
        List<Task> tasks;
        if (meetingId != null) {
            tasks = taskRepository.findByMeetingId(meetingId);
        } else if (workspaceId != null) {
            tasks = taskRepository.findByWorkspaceId(workspaceId);
        } else if (assigneeId != null) {
            tasks = taskRepository.findByAssigneeId(assigneeId);
        } else {
            throw new IllegalArgumentException("meetingId, workspaceId, assigneeId 중 하나는 필요합니다.");
        }
        return tasks.stream()
                .filter(t -> status == null || status == t.getStatus())
                .filter(t -> dueBefore == null || (t.getDueDate() != null && t.getDueDate().isBefore(dueBefore)))
                .map(TaskResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TaskStatsResponse getTaskStats(Long workspaceId, Long meetingId) {
        List<Task> tasks;
        if (meetingId != null) {
            tasks = taskRepository.findByMeetingId(meetingId);
        } else if (workspaceId != null) {
            tasks = taskRepository.findByWorkspaceId(workspaceId);
        } else {
            throw new IllegalArgumentException("workspaceId 또는 meetingId 중 하나는 필요합니다.");
        }
        Map<TaskStatus, Long> countMap = tasks.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getStatus() != null ? t.getStatus() : TaskStatus.TODO,
                        Collectors.counting()));
        return new TaskStatsResponse(workspaceId, meetingId, countMap);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long id, Long userId) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        checkAccess(task.getWorkspaceId(), task.getCreatedBy(), userId);
        return new TaskResponse(task);
    }

    @Transactional
    public TaskResponse createTask(TaskCreateRequest request, Long userId) {
        if (request.getWorkspaceId() != null &&
                !workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(request.getWorkspaceId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setAssigneeId(request.getAssigneeId());
        task.setAssigneeName(request.getAssigneeName());
        task.setDueDate(request.getDueDate());
        task.setWorkspaceId(request.getWorkspaceId());
        task.setMeetingId(request.getMeetingId());
        task.setCreatedBy(userId);
        task.setSource(TaskSource.MANUAL);
        task.setStatus(TaskStatus.TODO);
        return new TaskResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateTask(Long id, TaskUpdateRequest request, Long userId) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        checkAccess(task.getWorkspaceId(), task.getCreatedBy(), userId);

        if (request.getTitle() != null)        task.setTitle(request.getTitle());
        if (request.getDescription() != null)  task.setDescription(request.getDescription());
        if (request.getAssigneeId() != null)   task.setAssigneeId(request.getAssigneeId());
        if (request.getAssigneeName() != null) task.setAssigneeName(request.getAssigneeName());
        if (request.getDueDate() != null)      task.setDueDate(request.getDueDate());
        if (request.getStatus() != null)       task.setStatus(request.getStatus());

        return new TaskResponse(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(Long id, Long userId) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        checkAccess(task.getWorkspaceId(), task.getCreatedBy(), userId);
        taskRepository.delete(task);
    }

    private void checkAccess(Long workspaceId, Long createdBy, Long userId) {
        if (workspaceId != null) {
            if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, userId)) {
                throw new BusinessException(ErrorCode.TASK_ACCESS_DENIED);
            }
        } else if (createdBy != null && !createdBy.equals(userId)) {
            throw new BusinessException(ErrorCode.TASK_ACCESS_DENIED);
        }
    }
}
