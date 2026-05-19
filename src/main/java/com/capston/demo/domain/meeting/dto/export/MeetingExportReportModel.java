package com.capston.demo.domain.meeting.dto.export;

import lombok.Getter;

import java.util.List;

/**
 * PDF 템플릿(meeting-export/report.html)에 바인딩하는 뷰 모델.
 * Thymeleaf 변수명: {@code report}
 */
@Getter
public class MeetingExportReportModel {

    private final String workspaceName;
    private final String meetingTitle;
    private final String meetingCreatedAt;
    private final String analyzedAt;
    private final String summary;
    private final List<String> keywords;
    private final long taskTotal;
    private final long taskTodo;
    private final long taskInProgress;
    private final long taskDone;
    private final List<TaskRow> tasks;
    private final boolean includeEvents;
    private final List<EventRow> events;
    private final String exportedAt;

    public MeetingExportReportModel(String workspaceName, String meetingTitle, String meetingCreatedAt,
                                    String analyzedAt, String summary, List<String> keywords,
                                    long taskTotal, long taskTodo, long taskInProgress, long taskDone,
                                    List<TaskRow> tasks, boolean includeEvents, List<EventRow> events,
                                    String exportedAt) {
        this.workspaceName = workspaceName;
        this.meetingTitle = meetingTitle;
        this.meetingCreatedAt = meetingCreatedAt;
        this.analyzedAt = analyzedAt;
        this.summary = summary;
        this.keywords = keywords;
        this.taskTotal = taskTotal;
        this.taskTodo = taskTodo;
        this.taskInProgress = taskInProgress;
        this.taskDone = taskDone;
        this.tasks = tasks;
        this.includeEvents = includeEvents;
        this.events = events;
        this.exportedAt = exportedAt;
    }

    @Getter
    public static class TaskRow {
        private final String statusLabel;
        private final String title;
        private final String assigneeName;
        private final String dueDate;
        private final String description;

        public TaskRow(String statusLabel, String title, String assigneeName, String dueDate, String description) {
            this.statusLabel = statusLabel;
            this.title = title;
            this.assigneeName = assigneeName;
            this.dueDate = dueDate;
            this.description = description;
        }
    }

    /** 일정 표 1행 — 기간·담당자(createdByName) */
    @Getter
    public static class EventRow {
        private final String title;
        private final String period;
        private final String assigneeName;

        public EventRow(String title, String period, String assigneeName) {
            this.title = title;
            this.period = period;
            this.assigneeName = assigneeName;
        }
    }
}
