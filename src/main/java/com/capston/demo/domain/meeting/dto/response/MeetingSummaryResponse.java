package com.capston.demo.domain.meeting.dto.response;

import com.capston.demo.domain.calender.entity.TaskStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
public class MeetingSummaryResponse {

    private final Long meetingId;
    private final String title;
    private final String summary;
    private final List<String> keywords;
    private final LocalDateTime analyzedAt;
    private final TaskStats taskStats;
    private final long eventCount;

    public MeetingSummaryResponse(Long meetingId, String title, String summary,
                                   List<String> keywords, LocalDateTime analyzedAt,
                                   Map<TaskStatus, Long> statusCountMap, long eventCount) {
        this.meetingId = meetingId;
        this.title = title;
        this.summary = summary;
        this.keywords = keywords;
        this.analyzedAt = analyzedAt;
        this.taskStats = new TaskStats(statusCountMap);
        this.eventCount = eventCount;
    }

    @Getter
    public static class TaskStats {
        private final long total;
        private final long todo;
        private final long inProgress;
        private final long done;

        public TaskStats(Map<TaskStatus, Long> countMap) {
            this.todo = countMap.getOrDefault(TaskStatus.TODO, 0L);
            this.inProgress = countMap.getOrDefault(TaskStatus.IN_PROGRESS, 0L);
            this.done = countMap.getOrDefault(TaskStatus.DONE, 0L);
            this.total = this.todo + this.inProgress + this.done;
        }
    }
}
