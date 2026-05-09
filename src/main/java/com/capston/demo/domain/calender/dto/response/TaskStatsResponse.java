package com.capston.demo.domain.calender.dto.response;

import com.capston.demo.domain.calender.entity.TaskStatus;
import lombok.Getter;

import java.util.Map;

@Getter
public class TaskStatsResponse {

    private final Long workspaceId;
    private final Long meetingId;
    private final long total;
    private final long todo;
    private final long inProgress;
    private final long done;

    public TaskStatsResponse(Long workspaceId, Long meetingId, Map<TaskStatus, Long> countMap) {
        this.workspaceId = workspaceId;
        this.meetingId = meetingId;
        this.todo = countMap.getOrDefault(TaskStatus.TODO, 0L);
        this.inProgress = countMap.getOrDefault(TaskStatus.IN_PROGRESS, 0L);
        this.done = countMap.getOrDefault(TaskStatus.DONE, 0L);
        this.total = this.todo + this.inProgress + this.done;
    }
}
