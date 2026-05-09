package com.capston.demo.domain.calender.dto.request;

import com.capston.demo.domain.calender.entity.TaskStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class TaskUpdateRequest {

    private String title;

    private String description;

    private Long assigneeId;

    private String assigneeName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dueDate;

    private TaskStatus status;
}
