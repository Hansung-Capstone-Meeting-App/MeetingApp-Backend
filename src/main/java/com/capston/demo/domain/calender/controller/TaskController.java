package com.capston.demo.domain.calender.controller;

import com.capston.demo.domain.calender.controllerDocs.TaskControllerDocs;
import com.capston.demo.domain.calender.dto.request.TaskCreateRequest;
import com.capston.demo.domain.calender.dto.request.TaskUpdateRequest;
import com.capston.demo.domain.calender.dto.response.TaskResponse;
import com.capston.demo.domain.calender.dto.response.TaskStatsResponse;
import com.capston.demo.domain.calender.entity.TaskStatus;
import com.capston.demo.domain.calender.service.TaskService;
import com.capston.demo.global.security.CustomUserDetails;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController implements TaskControllerDocs {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long meetingId,
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime dueBefore
    ) {
        return ResponseEntity.ok(taskService.getTasks(meetingId, workspaceId, assigneeId,
                status, dueBefore, userDetails.getUserId()));
    }

    @GetMapping("/stats")
    public ResponseEntity<TaskStatsResponse> getTaskStats(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) Long meetingId
    ) {
        return ResponseEntity.ok(taskService.getTaskStats(workspaceId, meetingId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(taskService.getTask(id, userDetails.getUserId()));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TaskCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(taskService.createTask(request, userDetails.getUserId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody TaskUpdateRequest request
    ) {
        return ResponseEntity.ok(taskService.updateTask(id, request, userDetails.getUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        taskService.deleteTask(id, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }
}
