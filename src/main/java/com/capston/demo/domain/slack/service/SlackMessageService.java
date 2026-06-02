package com.capston.demo.domain.slack.service;

import com.capston.demo.domain.ai.dto.response.GeminiAnalyzeResponse;
import com.capston.demo.domain.ai.dto.response.TranscribeResponse;
import com.capston.demo.domain.calender.entity.Event;
import com.capston.demo.domain.calender.entity.Task;
import com.capston.demo.domain.calender.entity.TaskStatus;
import com.capston.demo.domain.meeting.dto.response.MeetingNotionExportResponse;
import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import com.capston.demo.domain.meeting.service.MeetingExportService;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.BlockCompositions;
import com.slack.api.model.block.element.BlockElements;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackMessageService {

    private static final int MAX_BLOCK_TEXT = 2900;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> WORKFLOW_STEPS = List.of(
            "파일 수집",
            "STT 변환",
            "화자 매핑",
            "분석 정리",
            "결과 전달"
    );

    private final MeetingExportService meetingExportService;

    @Value("${slack.bot-token}")
    private String botToken;

    public record WorkflowMessage(String rootTs, String threadTs) {
    }

    public record WorkflowStatus(
            String meetingTitle,
            String headline,
            String detail,
            int currentStep,
            boolean completed,
            Integer speakerCount,
            Integer taskCount,
            Integer eventCount
    ) {
    }

    public WorkflowMessage postWorkflowStarted(String channelId, String meetingTitle) throws Exception {
        ChatPostMessageResponse response = client().chatPostMessage(r -> r
                .channel(channelId)
                .text("회의 분석을 시작했습니다.")
                .blocks(renderStatusBlocks(new WorkflowStatus(
                        meetingTitle,
                        "회의 분석을 시작했습니다",
                        "업로드 파일을 가져와서 STT 변환을 준비합니다.",
                        1,
                        false,
                        null,
                        null,
                        null
                )))
        );
        if (!response.isOk()) {
            throw new IllegalStateException("Failed to post workflow start: " + response.getError());
        }
        return new WorkflowMessage(response.getTs(), response.getTs());
    }

    public void updateWorkflowStatus(String channelId, String rootTs, WorkflowStatus status) {
        try {
            client().chatUpdate(r -> r
                    .channel(channelId)
                    .ts(rootTs)
                    .text(status.headline())
                    .blocks(renderStatusBlocks(status))
            );
        } catch (Exception e) {
            log.warn("Failed to update workflow status. channelId={}, ts={}", channelId, rootTs, e);
        }
    }

    public void postSpeakerMappingPrompt(String channelId, String threadTs, String meetingTitle, String transcriptId,
                                         List<String> speakerLabels, List<TranscribeResponse.SegmentInfo> segments) {
        try {
            List<LayoutBlock> blocks = new ArrayList<>();
            blocks.add(Blocks.section(s -> s.text(BlockCompositions.markdownText(
                    "*STT 변환이 완료되었습니다*\n" +
                            "*" + meetingTitle + "*\n" +
                            "결과를 사람 기준으로 정리하려면 화자를 실제 Slack 멤버와 먼저 연결해야 합니다."
            ))));
            blocks.add(Blocks.context(c -> c.elements(List.of(
                    BlockCompositions.markdownText("아래 샘플 발언을 보고 각 화자를 Slack 멤버와 매핑하세요.")
            ))));
            blocks.add(Blocks.divider());

            for (String label : speakerLabels) {
                StringBuilder builder = new StringBuilder();
                builder.append("*화자 ").append(label).append("*\n");
                segments.stream()
                        .filter(segment -> label.equals(segment.getSpeakerLabel()))
                        .limit(2)
                        .forEach(segment -> builder.append("• `")
                                .append(formatTime(segment.getStartSec()))
                                .append("` ")
                                .append(truncate(resolveSegmentText(segment), 140))
                                .append("\n"));
                blocks.add(Blocks.section(s -> s.text(BlockCompositions.markdownText(trimToBlock(builder.toString())))));
            }

            blocks.add(Blocks.actions(a -> a
                    .blockId("speaker_mapping_actions")
                    .elements(List.of(
                            BlockElements.button(b -> b
                                    .text(BlockCompositions.plainText("화자 매핑 시작"))
                                    .actionId("open_speaker_mapping")
                                    .style("primary")
                                    .value(transcriptId)
                            )
                    ))
            ));

            client().chatPostMessage(r -> r
                    .channel(channelId)
                    .threadTs(threadTs)
                    .text("화자 매핑이 필요합니다.")
                    .blocks(blocks)
            );
        } catch (Exception e) {
            log.warn("Failed to post speaker mapping prompt. channelId={}", channelId, e);
        }
    }

    public void postFinalResult(String channelId, String threadTs,
                                GeminiAnalyzeResponse analysis, List<Task> tasks, List<Event> events,
                                List<MeetingTranscript.SpeakerMappingEmbedded> speakerMappings,
                                MeetingNotionExportResponse notionExport) {
        try {
            List<LayoutBlock> blocks = new ArrayList<>();
            int speakerCount = (int) speakerMappings.stream()
                    .map(MeetingTranscript.SpeakerMappingEmbedded::getSpeakerLabel)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .count();

            blocks.add(Blocks.header(h -> h.text(BlockCompositions.plainText("회의 분석 리포트"))));
            blocks.add(Blocks.section(s -> s.text(BlockCompositions.markdownText(
                    "*" + trimToBlock(analysis.getSummary()) + "*"
            ))));
            blocks.add(Blocks.section(s -> s.fields(List.of(
                    BlockCompositions.markdownText("*화자*\n" + speakerCount + "명"),
                    BlockCompositions.markdownText("*할 일*\n" + tasks.size() + "건"),
                    BlockCompositions.markdownText("*일정*\n" + events.size() + "건"),
                    BlockCompositions.markdownText("*키워드*\n" + analysis.getKeywords().size() + "개")
            ))));

            appendKeywordSection(blocks, analysis.getKeywords());
            appendTaskSection(blocks, tasks, speakerMappings);
            appendEventSection(blocks, events);
            appendSpeakerSection(blocks, speakerMappings);
            appendActionSection(blocks, notionExport);

            client().chatPostMessage(r -> r
                    .channel(channelId)
                    .threadTs(threadTs)
                    .text("회의 분석 리포트가 준비되었습니다.")
                    .blocks(blocks)
            );
        } catch (Exception e) {
            log.warn("Failed to post final result. channelId={}", channelId, e);
        }
    }

    public void uploadPdfsToThread(String channelId, String threadTs, Long meetingId, Long userId, String meetingTitle) {
        try {
            var reportResult = meetingExportService.exportPdf(meetingId, userId, true);
            File reportFile = Files.createTempFile("meeting-report-", ".pdf").toFile();
            Files.write(reportFile.toPath(), reportResult.pdfBytes());
            client().filesUploadV2(r -> r
                    .channel(channelId)
                    .threadTs(threadTs)
                    .file(reportFile)
                    .filename(reportResult.fileName())
                    .title("회의 리포트 PDF - " + meetingTitle)
            );
            reportFile.delete();

            var transcriptResult = meetingExportService.exportTranscriptPdf(meetingId, userId, true);
            File transcriptFile = Files.createTempFile("meeting-transcript-", ".pdf").toFile();
            Files.write(transcriptFile.toPath(), transcriptResult.pdfBytes());
            client().filesUploadV2(r -> r
                    .channel(channelId)
                    .threadTs(threadTs)
                    .file(transcriptFile)
                    .filename(transcriptResult.fileName())
                    .title("대화록 PDF - " + meetingTitle)
            );
            transcriptFile.delete();
        } catch (Exception e) {
            log.warn("Failed to upload PDFs. channelId={}, meetingId={}", channelId, meetingId, e);
        }
    }

    public void postErrorMessage(String channelId, String threadTs, String text) {
        try {
            client().chatPostMessage(r -> {
                var req = r.channel(channelId).text(text);
                if (threadTs != null) {
                    req.threadTs(threadTs);
                }
                return req;
            });
        } catch (Exception e) {
            log.warn("Failed to post error message. channelId={}", channelId, e);
        }
    }

    private MethodsClient client() {
        return Slack.getInstance().methods(botToken);
    }

    private List<LayoutBlock> renderStatusBlocks(WorkflowStatus status) {
        List<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(Blocks.header(h -> h.text(BlockCompositions.plainText("회의 분석 진행 상태"))));
        blocks.add(Blocks.section(s -> s.text(BlockCompositions.markdownText(
                "*" + status.meetingTitle() + "*\n" +
                        "*" + status.headline() + "*\n" +
                        status.detail()
        ))));
        blocks.add(Blocks.section(s -> s.text(BlockCompositions.markdownText(renderStepProgress(status.currentStep(), status.completed())))));

        List<com.slack.api.model.block.composition.TextObject> fields = new ArrayList<>();
        if (status.speakerCount() != null) {
            fields.add(BlockCompositions.markdownText("*화자*\n" + status.speakerCount() + "명"));
        }
        if (status.taskCount() != null) {
            fields.add(BlockCompositions.markdownText("*할 일*\n" + status.taskCount() + "건"));
        }
        if (status.eventCount() != null) {
            fields.add(BlockCompositions.markdownText("*일정*\n" + status.eventCount() + "건"));
        }
        if (!fields.isEmpty()) {
            blocks.add(Blocks.section(s -> s.fields(fields)));
        }
        return blocks;
    }

    private String renderStepProgress(int currentStep, boolean completed) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < WORKFLOW_STEPS.size(); i++) {
            int stepNumber = i + 1;
            String prefix;
            if (completed || stepNumber < currentStep) {
                prefix = "완료";
            } else if (stepNumber == currentStep) {
                prefix = "진행";
            } else {
                prefix = "대기";
            }
            builder.append(prefix)
                    .append(" · ")
                    .append(WORKFLOW_STEPS.get(i))
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private void appendKeywordSection(List<LayoutBlock> blocks, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return;
        }
        String joined = keywords.stream()
                .filter(StringUtils::hasText)
                .limit(12)
                .map(keyword -> "`#" + keyword + "`")
                .collect(Collectors.joining(" "));
        if (!joined.isBlank()) {
            blocks.add(Blocks.section(s -> s.text(BlockCompositions.markdownText("*핵심 키워드*\n" + joined))));
        }
    }

    private void appendTaskSection(List<LayoutBlock> blocks, List<Task> tasks,
                                   List<MeetingTranscript.SpeakerMappingEmbedded> speakerMappings) {
        blocks.add(Blocks.divider());
        blocks.add(Blocks.section(s -> s.text(BlockCompositions.markdownText("*개인별 할 일*"))));

        if (tasks.isEmpty()) {
            blocks.add(Blocks.context(c -> c.elements(List.of(
                    BlockCompositions.markdownText("이번 회의에서 새로 추출된 할 일이 없습니다.")
            ))));
            return;
        }

        Map<String, List<Task>> byAssignee = new LinkedHashMap<>();
        for (Task task : tasks) {
            String key = StringUtils.hasText(task.getAssigneeName()) ? task.getAssigneeName() : "미정";
            byAssignee.computeIfAbsent(key, ignored -> new ArrayList<>()).add(task);
        }

        for (Map.Entry<String, List<Task>> entry : byAssignee.entrySet()) {
            String assigneeDisplay = resolveAssigneeDisplay(entry.getKey(), speakerMappings);
            StringBuilder builder = new StringBuilder();
            builder.append("*").append(assigneeDisplay).append("*\n");
            for (Task task : entry.getValue()) {
                builder.append("• ")
                        .append(taskStatusBadge(task.getStatus()))
                        .append(" ")
                        .append(task.getTitle());
                if (task.getDueDate() != null) {
                    builder.append(" `").append(task.getDueDate().format(DATE_FMT)).append("`");
                }
                if (StringUtils.hasText(task.getDescription())) {
                    builder.append("\n  ")
                            .append(truncate(task.getDescription().trim(), 120));
                }
                builder.append("\n");
            }
            blocks.add(Blocks.section(s -> s.text(BlockCompositions.markdownText(trimToBlock(builder.toString())))));
        }
    }

    private void appendEventSection(List<LayoutBlock> blocks, List<Event> events) {
        blocks.add(Blocks.divider());
        blocks.add(Blocks.section(s -> s.text(BlockCompositions.markdownText("*일정 캘린더*"))));

        if (events.isEmpty()) {
            blocks.add(Blocks.context(c -> c.elements(List.of(
                    BlockCompositions.markdownText("이번 회의에서 새로 추출된 일정이 없습니다.")
            ))));
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (Event event : events.stream().limit(8).toList()) {
            builder.append("• *").append(event.getTitle()).append("*\n")
                    .append("  ")
                    .append(formatEventPeriod(event));
            if (StringUtils.hasText(event.getCreatedByName())) {
                builder.append(" · ").append(event.getCreatedByName());
            }
            if (StringUtils.hasText(event.getLocation())) {
                builder.append(" · ").append(event.getLocation());
            }
            builder.append("\n");
        }
        blocks.add(Blocks.section(s -> s.text(BlockCompositions.markdownText(trimToBlock(builder.toString())))));
    }

    private void appendSpeakerSection(List<LayoutBlock> blocks,
                                      List<MeetingTranscript.SpeakerMappingEmbedded> speakerMappings) {
        if (speakerMappings == null || speakerMappings.isEmpty()) {
            return;
        }
        blocks.add(Blocks.divider());
        StringBuilder builder = new StringBuilder("*화자 연결 정보*\n");
        for (MeetingTranscript.SpeakerMappingEmbedded mapping : speakerMappings) {
            builder.append("• ")
                    .append(mapping.getSpeakerLabel())
                    .append(" → ")
                    .append(resolveSpeakerIdentity(mapping))
                    .append("\n");
        }
        blocks.add(Blocks.section(s -> s.text(BlockCompositions.markdownText(trimToBlock(builder.toString())))));
    }

    private void appendActionSection(List<LayoutBlock> blocks, MeetingNotionExportResponse notionExport) {
        List<com.slack.api.model.block.element.BlockElement> elements = new ArrayList<>();
        if (notionExport != null && StringUtils.hasText(notionExport.getNotionUrl())) {
            elements.add(BlockElements.button(b -> b
                    .text(BlockCompositions.plainText("Notion에서 보기"))
                    .url(notionExport.getNotionUrl())
                    .style("primary")
            ));
        }
        if (!elements.isEmpty()) {
            blocks.add(Blocks.divider());
            blocks.add(Blocks.actions(a -> a.elements(elements)));
        } else {
            blocks.add(Blocks.divider());
            blocks.add(Blocks.context(c -> c.elements(List.of(
                    BlockCompositions.markdownText("Notion이 연결되어 있지 않으면 Slack 스레드와 PDF 중심으로 결과를 확인할 수 있습니다.")
            ))));
        }
    }

    private String resolveAssigneeDisplay(String assigneeName,
                                          List<MeetingTranscript.SpeakerMappingEmbedded> speakerMappings) {
        return speakerMappings.stream()
                .filter(mapping -> assigneeName.equals(mapping.getUserName()))
                .findFirst()
                .map(this::resolveSpeakerIdentity)
                .orElse(assigneeName);
    }

    private String resolveSpeakerIdentity(MeetingTranscript.SpeakerMappingEmbedded mapping) {
        if (StringUtils.hasText(mapping.getSlackUserId())) {
            return "<@" + mapping.getSlackUserId() + ">";
        }
        if (StringUtils.hasText(mapping.getUserName())) {
            return mapping.getUserName();
        }
        if (mapping.getUserId() != null) {
            return "user#" + mapping.getUserId();
        }
        return mapping.getSpeakerLabel();
    }

    private String resolveSegmentText(TranscribeResponse.SegmentInfo segment) {
        if (StringUtils.hasText(segment.getDisplayContent())) {
            return segment.getDisplayContent().trim();
        }
        if (StringUtils.hasText(segment.getContent())) {
            return segment.getContent().trim();
        }
        if (StringUtils.hasText(segment.getCorrectedContent())) {
            return segment.getCorrectedContent().trim();
        }
        if (StringUtils.hasText(segment.getOriginalContent())) {
            return segment.getOriginalContent().trim();
        }
        return "";
    }

    private String formatEventPeriod(Event event) {
        if (Boolean.TRUE.equals(event.getIsAllDay())) {
            String start = event.getStartAt() != null ? event.getStartAt().toLocalDate().format(DATE_FMT) : "-";
            String end = event.getEndAt() != null ? event.getEndAt().toLocalDate().format(DATE_FMT) : start;
            if (start.equals(end)) {
                return start + " 종일";
            }
            return start + " ~ " + end + " 종일";
        }
        String start = event.getStartAt() != null ? event.getStartAt().format(DATE_TIME_FMT) : "-";
        String end = event.getEndAt() != null ? event.getEndAt().format(DATE_TIME_FMT) : "-";
        return start + " ~ " + end;
    }

    private String taskStatusBadge(TaskStatus status) {
        if (status == null) {
            return "[TODO]";
        }
        return switch (status) {
            case TODO -> "[TODO]";
            case IN_PROGRESS -> "[진행중]";
            case DONE -> "[완료]";
        };
    }

    private String formatTime(float seconds) {
        int total = (int) seconds;
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int secs = total % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%02d:%02d", minutes, secs);
    }

    private String truncate(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen) + "...";
    }

    private String trimToBlock(String text) {
        return truncate(text, MAX_BLOCK_TEXT);
    }
}
