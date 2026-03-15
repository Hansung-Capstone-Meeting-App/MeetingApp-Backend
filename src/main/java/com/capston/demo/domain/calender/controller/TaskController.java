package com.capston.demo.domain.calender.controller;

import com.capston.demo.domain.calender.controllerDocs.TaskControllerDocs;
import com.capston.demo.domain.calender.dto.response.TaskResponse;
import com.capston.demo.domain.calender.service.TaskService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController implements TaskControllerDocs {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(
            @RequestParam(required = false) Long meetingId,
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) Long assigneeId
    ) {
        return ResponseEntity.ok(taskService.getTasks(meetingId, workspaceId, assigneeId));
    }
}
