package com.capston.demo.domain.meeting.service;

import com.capston.demo.domain.calender.entity.Event;
import com.capston.demo.domain.calender.entity.Task;
import com.capston.demo.domain.calender.entity.TaskStatus;
import com.capston.demo.domain.calender.repository.EventRepository;
import com.capston.demo.domain.calender.repository.TaskRepository;
import com.capston.demo.domain.meeting.dto.export.MeetingExportReportModel;
import com.capston.demo.domain.meeting.dto.export.MeetingExportReportModel.EventRow;
import com.capston.demo.domain.meeting.dto.export.MeetingExportReportModel.TaskRow;
import com.capston.demo.domain.meeting.dto.export.MeetingTranscriptExportModel;
import com.capston.demo.domain.meeting.dto.export.MeetingTranscriptExportModel.SegmentRow;
import com.capston.demo.domain.meeting.dto.response.MeetingNotionExportResponse;
import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.meeting.repository.MeetingTranscriptMongoRepository;
import com.capston.demo.domain.user.entity.UserNotionAccount;
import com.capston.demo.domain.user.entity.Workspace;
import com.capston.demo.domain.user.repository.UserNotionAccountRepository;
import com.capston.demo.domain.user.repository.WorkspaceMemberRepository;
import com.capston.demo.domain.user.repository.WorkspaceRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 회의 리포트 PDF 생성 서비스.
 *
 * 흐름: MySQL/MongoDB 조회 → {@link MeetingExportReportModel} 조립
 *       → Thymeleaf HTML 렌더링 → openhtmltopdf 로 PDF 변환
 *
 * 템플릿: src/main/resources/templates/meeting-export/report.html
 * 한글 폰트: src/main/resources/fonts/malgun.ttf (classpath → 임시 파일 캐시)
 */
@Service
@RequiredArgsConstructor
public class MeetingExportService {

    /** CSS font-family 와 useFont 등록명이 일치해야 함 */
    private static final String FONT_FAMILY = "MalgunGothic";
    private static final String FONT_PATH = "/fonts/malgun.ttf";
    /** JAR 실행 시에도 TTF 를 File 로 넘기기 위한 1회성 캐시 */
    private static volatile File cachedFontFile;
    /** Thymeleaf 템플릿 경로 (확장자 제외) */
    private static final String TEMPLATE = "meeting-export/report";
    private static final String TRANSCRIPT_TEMPLATE = "meeting-export/transcript";
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<TaskStatus> TASK_STATUS_ORDER = List.of(
            TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.DONE);

    private final MeetingRepository meetingRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final MeetingTranscriptMongoRepository transcriptRepository;
    private final TaskRepository taskRepository;
    private final EventRepository eventRepository;
    private final SpringTemplateEngine templateEngine;
    private final UserNotionAccountRepository userNotionAccountRepository;
    private final NotionMeetingNotesService notionMeetingNotesService;

    /** PDF 바이트 + 다운로드용 파일명 */
    public record ExportResult(byte[] pdfBytes, String fileName) {}

    /**
     * 회의 1건을 PDF 로 변환한다.
     *
     * @param meetingId     회의 ID (MySQL meetings.id)
     * @param userId        JWT 사용자 ID (접근 권한 검증용)
     * @param includeEvents true: 일정 표 포함 / false: 요약·할일만
     */
    @Transactional(readOnly = true)
    public ExportResult exportPdf(Long meetingId, Long userId, boolean includeEvents) {
        MeetingExportReportModel model = buildReportModel(meetingId, userId, includeEvents);
        byte[] pdfBytes = renderPdf(TEMPLATE, "report", model);
        return new ExportResult(pdfBytes, buildReportFileName(model.getMeetingTitle()));
    }

    /**
     * 회의 대화록(전사 세그먼트)을 PDF로 변환한다.
     *
     * @param meetingId         회의 ID (MySQL meetings.id)
     * @param userId            JWT 사용자 ID (접근 권한 검증용)
     * @param includeTimestamps true: 발화 시각 표시 / false: 화자·내용만
     */
    @Transactional(readOnly = true)
    public ExportResult exportTranscriptPdf(Long meetingId, Long userId, boolean includeTimestamps) {
        MeetingTranscriptExportModel model = buildTranscriptModel(meetingId, userId, includeTimestamps);
        byte[] pdfBytes = renderPdf(TRANSCRIPT_TEMPLATE, "transcript", model);
        return new ExportResult(pdfBytes, buildTranscriptFileName(model.getMeetingTitle()));
    }

    /**
     * 회의 리포트를 Notion 회의록 DB에보낸다.
     * 이미보낸 적 있으면 동일 페이지를 갱신한다.
     */
    @Transactional
    public MeetingNotionExportResponse exportToNotion(Long meetingId, Long userId, boolean includeEvents) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        checkAccess(meeting, userId);

        UserNotionAccount notionAccount = userNotionAccountRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTION_ACCOUNT_NOT_LINKED));
        if (!StringUtils.hasText(notionAccount.getMeetingNotesDatabaseId())) {
            throw new BusinessException(ErrorCode.NOTION_MEETING_NOTES_DB_NOT_REGISTERED);
        }

        boolean updating = StringUtils.hasText(meeting.getNotionPageId());
        MeetingExportReportModel model = buildReportModel(meetingId, userId, includeEvents);

        String pageId = notionMeetingNotesService.exportMeetingNotes(
                model,
                notionAccount.getAccessToken(),
                notionAccount.getMeetingNotesDatabaseId(),
                meeting.getNotionPageId(),
                meeting.getCreatedAt()
        );

        meeting.setNotionPageId(pageId);
        meeting.setNotionExportedAt(LocalDateTime.now());
        meetingRepository.save(meeting);

        return new MeetingNotionExportResponse(
                meetingId,
                pageId,
                NotionMeetingNotesService.toNotionPageUrl(pageId),
                updating
        );
    }

    /** PDF·Notion export 공통 — 요약·할일·일정 데이터 조립 */
    @Transactional(readOnly = true)
    public MeetingExportReportModel buildReportModel(Long meetingId, Long userId, boolean includeEvents) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        checkAccess(meeting, userId);

        MeetingTranscript transcript = transcriptRepository
                .findByMeetingIdOrderByCreatedAtDesc(meetingId)
                .stream()
                .findFirst()
                .orElse(null);
        if (transcript == null || !StringUtils.hasText(transcript.getSummary())) {
            throw new BusinessException(ErrorCode.MEETING_EXPORT_NOT_READY);
        }

        String workspaceName = null;
        if (meeting.getWorkspaceId() != null) {
            workspaceName = workspaceRepository.findById(meeting.getWorkspaceId())
                    .map(Workspace::getName)
                    .orElse(null);
        }

        List<Task> tasks = taskRepository.findByMeetingId(meetingId);
        Map<TaskStatus, Long> statusCounts = tasks.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getStatus() != null ? t.getStatus() : TaskStatus.TODO,
                        Collectors.counting()));

        List<TaskRow> taskRows = tasks.stream()
                .sorted(taskComparator())
                .map(this::toTaskRow)
                .toList();

        List<EventRow> eventRows = List.of();
        if (includeEvents) {
            eventRows = eventRepository.findByMeetingId(meetingId).stream()
                    .sorted(Comparator.comparing(Event::getStartAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(this::toEventRow)
                    .toList();
        }

        return new MeetingExportReportModel(
                workspaceName,
                meeting.getTitle(),
                formatDateTime(meeting.getCreatedAt()),
                formatDateTime(transcript.getAnalyzedAt()),
                transcript.getSummary(),
                transcript.getKeywords() != null ? transcript.getKeywords() : List.of(),
                tasks.size(),
                statusCounts.getOrDefault(TaskStatus.TODO, 0L),
                statusCounts.getOrDefault(TaskStatus.IN_PROGRESS, 0L),
                statusCounts.getOrDefault(TaskStatus.DONE, 0L),
                taskRows,
                includeEvents,
                eventRows,
                LocalDateTime.now().format(DATE_TIME_FMT)
        );
    }

    /** 대화록 PDF — STT 세그먼트 조립 */
    @Transactional(readOnly = true)
    public MeetingTranscriptExportModel buildTranscriptModel(Long meetingId, Long userId, boolean includeTimestamps) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        checkAccess(meeting, userId);

        MeetingTranscript transcript = transcriptRepository
                .findByMeetingIdOrderByCreatedAtDesc(meetingId)
                .stream()
                .findFirst()
                .orElse(null);
        if (transcript == null || transcript.getSegments() == null || transcript.getSegments().isEmpty()) {
            throw new BusinessException(ErrorCode.TRANSCRIPT_EXPORT_NOT_READY);
        }

        String workspaceName = null;
        if (meeting.getWorkspaceId() != null) {
            workspaceName = workspaceRepository.findById(meeting.getWorkspaceId())
                    .map(Workspace::getName)
                    .orElse(null);
        }

        Map<String, String> labelToName = transcript.getSpeakerMappings().stream()
                .filter(m -> StringUtils.hasText(m.getUserName()))
                .collect(Collectors.toMap(
                        MeetingTranscript.SpeakerMappingEmbedded::getSpeakerLabel,
                        MeetingTranscript.SpeakerMappingEmbedded::getUserName,
                        (a, b) -> a
                ));

        List<SegmentRow> segmentRows = transcript.getSegments().stream()
                .sorted(Comparator.comparing(
                        MeetingTranscript.SegmentEmbedded::getSequence,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(segment -> toSegmentRow(segment, labelToName, includeTimestamps))
                .filter(row -> StringUtils.hasText(row.getContent()))
                .toList();

        if (segmentRows.isEmpty()) {
            throw new BusinessException(ErrorCode.TRANSCRIPT_EXPORT_NOT_READY);
        }

        return new MeetingTranscriptExportModel(
                workspaceName,
                meeting.getTitle(),
                formatDateTime(meeting.getCreatedAt()),
                formatDateTime(transcript.getCreatedAt()),
                includeTimestamps,
                segmentRows,
                LocalDateTime.now().format(DATE_TIME_FMT)
        );
    }

    /** Thymeleaf HTML → openhtmltopdf → PDF byte[] */
    private byte[] renderPdf(String template, String variableName, Object model) {
        Context context = new Context();
        context.setVariable(variableName, model);
        String html = templateEngine.process(template, context);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder(); // PDF 렌더러 빌더
            // 제목(700)·표헤더(700) 등 굵은 글씨도 동일 한글 폰트 사용 (미등록 시 # 깨짐)
            registerFont(builder, 400); // 400 폰트 등록
            registerFont(builder, 700); // 700 폰트 등록
            builder.withHtmlContent(html, "/"); // HTML 콘텐츠 등록
            builder.toStream(out); // 바이트 스트림 출력
            builder.run(); // PDF 렌더링
            return out.toByteArray(); // 바이트 배열 반환
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PDF_GENERATION_FAILED, e); // 회의 리포트 PDF 생성 실패
        }
    }

    private void registerFont(PdfRendererBuilder builder, int weight) { // 폰트 등록
        builder.useFont(
                resolveFontFile(), // 폰트 파일 검색
                FONT_FAMILY, // 폰트 패밀리
                weight, // 폰트 무게
                BaseRendererBuilder.FontStyle.NORMAL, // 폰트 스타일
                false // 폰트 굵기
        );
    }

    /** classpath 폰트를 임시 TTF 파일로 복사 (openhtmltopdf 는 File 기반 로드가 안정적) */
    private File resolveFontFile() { // 폰트 파일 검색
        if (cachedFontFile != null && cachedFontFile.exists()) { // 폰트 파일이 있으면 반환
            return cachedFontFile; // 폰트 파일 반환
        }
        synchronized (MeetingExportService.class) { // 동기화
            if (cachedFontFile != null && cachedFontFile.exists()) { // 폰트 파일이 있으면 반환
                return cachedFontFile; // 폰트 파일 반환
            }
            try (InputStream in = getClass().getResourceAsStream(FONT_PATH)) { // 폰트 파일 스트림 검색
                if (in == null) { // 폰트 파일이 없으면 예외 발생
                    throw new BusinessException(ErrorCode.PDF_GENERATION_FAILED); // 회의 리포트 PDF 생성 실패
                }
                Path temp = Files.createTempFile("meeting-export-font-", ".ttf"); // 폰트 파일 임시 파일 생성
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                File fontFile = temp.toFile(); // 폰트 파일 임시 파일 생성
                fontFile.deleteOnExit(); // 폰트 파일 임시 파일 삭제
                cachedFontFile = fontFile; // 폰트 파일 임시 파일 캐시
                return fontFile; // 폰트 파일 반환
            } catch (IOException e) { // 폰트 파일 임시 파일 생성 실패
                throw new BusinessException(ErrorCode.PDF_GENERATION_FAILED, e); // 회의 리포트 PDF 생성 실패
            }
        }
    }

    private Comparator<Task> taskComparator() { // 할일 비교
        return Comparator
                .comparing((Task t) -> {
                    TaskStatus status = t.getStatus() != null ? t.getStatus() : TaskStatus.TODO; // 상태가 없으면 TODO로 처리
                    int idx = TASK_STATUS_ORDER.indexOf(status); // 상태 인덱스 조회
                    return idx >= 0 ? idx : TASK_STATUS_ORDER.size(); // 상태 인덱스 반환
                })
                .thenComparing(t -> t.getDueDate() == null ? LocalDateTime.MAX : t.getDueDate()) // 마감 일자 비교
                .thenComparing(t -> t.getTitle() != null ? t.getTitle() : ""); // 제목 비교
    }

    private TaskRow toTaskRow(Task task) { // 할일 행 변환
        return new TaskRow(
                statusLabel(task.getStatus()),
                task.getTitle(), // 제목
                StringUtils.hasText(task.getAssigneeName()) ? task.getAssigneeName() : "-", // 담당자
                task.getDueDate() != null ? task.getDueDate().format(DATE_TIME_FMT) : "-", // 마감 일자
                truncate(task.getDescription(), 120) // 설명 자르기
        );
    }

    /** 일정 행 — 담당자는 Gemini 가 채운 createdByName (일정 제안·주도 화자) */
    private EventRow toEventRow(Event event) { // 일정 행 변환
        String period;
        if (Boolean.TRUE.equals(event.getIsAllDay())) {
            String start = event.getStartAt() != null ? event.getStartAt().format(DATE_FMT) : "-";
            String end = event.getEndAt() != null ? event.getEndAt().format(DATE_FMT) : start;
            period = start.equals(end) ? start + " (종일)" : start + " ~ " + end + " (종일)";
        } else {
            String start = formatDateTime(event.getStartAt());
            String end = formatDateTime(event.getEndAt());
            period = start + " ~ " + end;
        }
        return new EventRow(
                event.getTitle(),
                period,
                StringUtils.hasText(event.getCreatedByName()) ? event.getCreatedByName() : "-"
        );
    }

    private String statusLabel(TaskStatus status) {
        if (status == null) {
            return "할 일";
        }
        return switch (status) {
            case TODO -> "할 일";
            case IN_PROGRESS -> "진행 중";
            case DONE -> "완료";
        };
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FMT) : "-";
    }

    private String truncate(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen) + "…";
    }

    private String buildReportFileName(String meetingTitle) {
        String safeTitle = (StringUtils.hasText(meetingTitle) ? meetingTitle : "meeting")
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return safeTitle + "_meeting_report_" + date + ".pdf";
    }

    private String buildTranscriptFileName(String meetingTitle) {
        String safeTitle = (StringUtils.hasText(meetingTitle) ? meetingTitle : "meeting")
                .replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return safeTitle + "_transcript_" + date + ".pdf";
    }

    private SegmentRow toSegmentRow(MeetingTranscript.SegmentEmbedded segment,
                                    Map<String, String> labelToName,
                                    boolean includeTimestamps) {
        String speakerName = labelToName.getOrDefault(
                segment.getSpeakerLabel(),
                StringUtils.hasText(segment.getSpeakerLabel()) ? segment.getSpeakerLabel() : "화자");
        String timestamp = includeTimestamps ? formatTimestampRange(segment.getStartSec(), segment.getEndSec()) : null;
        return new SegmentRow(speakerName, timestamp, resolveSegmentContent(segment));
    }

    private String resolveSegmentContent(MeetingTranscript.SegmentEmbedded segment) {
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

    private String formatTimestampRange(Float startSec, Float endSec) {
        if (startSec == null && endSec == null) {
            return null;
        }
        if (startSec != null && endSec != null) {
            return formatSeconds(startSec) + " ~ " + formatSeconds(endSec);
        }
        return formatSeconds(startSec != null ? startSec : endSec);
    }

    private String formatSeconds(Float sec) {
        if (sec == null) {
            return "-";
        }
        int total = Math.max(0, sec.intValue());
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int seconds = total % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    /** MeetingService 와 동일: workspace 멤버 또는 Slack 회의(createdBy) 본인 */
    private void checkAccess(Meeting meeting, Long userId) {
        if (meeting.getWorkspaceId() != null) {
            if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(meeting.getWorkspaceId(), userId)) {
                throw new BusinessException(ErrorCode.MEETING_ACCESS_DENIED);
            }
        } else if (!userId.equals(meeting.getCreatedBy())) {
            throw new BusinessException(ErrorCode.MEETING_ACCESS_DENIED);
        }
    }
}
