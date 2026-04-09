package com.capston.demo.domain.ai.service;

import com.capston.demo.domain.ai.dto.internal.AssemblyAiTranscriptResult;
import com.capston.demo.domain.ai.dto.internal.GeminiAnalysisResult;
import com.capston.demo.domain.ai.dto.response.GeminiAnalyzeResponse;
import com.capston.demo.domain.ai.dto.response.TranscribeResponse;
import com.capston.demo.domain.calender.entity.Event;
import com.capston.demo.domain.calender.entity.EventParticipant;
import com.capston.demo.domain.calender.entity.ParticipantStatus;
import com.capston.demo.domain.calender.entity.Task;
import com.capston.demo.domain.calender.entity.TaskSource;
import com.capston.demo.domain.calender.entity.TaskStatus;
import com.capston.demo.domain.calender.repository.EventRepository;
import com.capston.demo.domain.calender.repository.TaskRepository;
import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.entity.MeetingRecording;
import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import com.capston.demo.domain.meeting.entity.RecordingStatus;
import com.capston.demo.domain.meeting.repository.MeetingRecordingRepository;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.meeting.repository.MeetingTranscriptMongoRepository;
import com.capston.demo.domain.recording.service.RecordingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingAnalysisService {
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final AssemblyAiService assemblyAiService;
    private final GeminiAiService geminiAiService;
    private final MeetingRepository meetingRepository;
    private final MeetingRecordingRepository recordingRepository;
    private final RecordingService recordingService;
    private final MeetingTranscriptMongoRepository transcriptRepository;
    private final EventRepository eventRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    // ── 1단계: STT (AssemblyAI) ────────────────────────────────────────────────

    @Transactional
    public TranscribeResponse transcribe(Long meetingId, Long recordingId, Long userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId));
        if (!meeting.getCreatedBy().equals(userId)) {
            throw new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId);
        }

        MeetingRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("녹음 파일을 찾을 수 없습니다. id=" + recordingId));

        recording.setStatus(RecordingStatus.PROCESSING);

        String audioUrl = recordingService.generateDownloadPresignedUrl(recordingId, userId).getPresignedUrl();
        AssemblyAiTranscriptResult stt = assemblyAiService.transcribe(audioUrl);

        MeetingTranscript transcript = new MeetingTranscript();
        transcript.setMeetingId(meetingId);
        transcript.setRecordingId(recordingId);
        transcript.setFullText(stt.getFullText());

        List<TranscribeResponse.SegmentInfo> segmentInfos = new ArrayList<>();
        List<MeetingTranscript.SegmentEmbedded> segments = new ArrayList<>();
        int sequence = 0;
        for (AssemblyAiTranscriptResult.Utterance utterance : stt.getUtterances()) {
            MeetingTranscript.SegmentEmbedded segment = new MeetingTranscript.SegmentEmbedded();
            segment.setSpeakerLabel(utterance.getSpeaker());
            segment.setContent(utterance.getText());
            segment.setStartSec((float) utterance.getStartSec());
            segment.setEndSec((float) utterance.getEndSec());
            segment.setSequence(sequence++);
            segments.add(segment);

            segmentInfos.add(new TranscribeResponse.SegmentInfo(
                    utterance.getSpeaker(),
                    utterance.getText(),
                    (float) utterance.getStartSec(),
                    (float) utterance.getEndSec()
            ));
        }
        transcript.setSegments(segments);

        MeetingTranscript saved = transcriptRepository.save(transcript);
        return new TranscribeResponse(saved.getId(), segmentInfos);
    }

    // ── 3단계: Gemini 분석 ─────────────────────────────────────────────────────

    @Transactional
    public GeminiAnalyzeResponse geminiAnalyze(String transcriptId, Long userId) {
        MeetingTranscript transcript = transcriptRepository.findById(transcriptId)
                .orElseThrow(() -> new IllegalArgumentException("트랜스크립트를 찾을 수 없습니다. id=" + transcriptId));

        Meeting meeting = meetingRepository.findById(transcript.getMeetingId())
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다."));
        if (!meeting.getCreatedBy().equals(userId)) {
            throw new IllegalArgumentException("트랜스크립트를 찾을 수 없습니다. id=" + transcriptId);
        }

        List<AssemblyAiTranscriptResult.Utterance> utterances = transcript.getSegments().stream()
                .map(s -> new AssemblyAiTranscriptResult.Utterance(
                        s.getSpeakerLabel(),
                        s.getContent(),
                        s.getStartSec() != null ? s.getStartSec() : 0.0,
                        s.getEndSec() != null ? s.getEndSec() : 0.0
                ))
                .collect(Collectors.toList());
        AssemblyAiTranscriptResult stt = new AssemblyAiTranscriptResult(transcript.getFullText(), utterances);

        List<GeminiAnalysisResult.SpeakerInfo> speakerInfos = transcript.getSpeakerMappings().stream()
                .filter(m -> m.getUserName() != null)
                .map(m -> new GeminiAnalysisResult.SpeakerInfo(m.getSpeakerLabel(), m.getUserId(), m.getUserName()))
                .collect(Collectors.toList());

        GeminiAnalysisResult analysis = geminiAiService.analyze(
                stt,
                speakerInfos,
                resolveMeetingDate(meeting),
                normalizeText(meeting.getTitle())
        );

        transcript.setSummary(normalizeText(analysis.getSummary()));
        transcript.setAnalyzedAt(LocalDateTime.now());
        transcript.setKeywords(normalizeKeywords(analysis.getKeywords()));

        List<GeminiAnalysisResult.ExtractedTask> filteredTasks = filterTasks(analysis.getTasks());
        List<GeminiAnalysisResult.ExtractedEvent> filteredEvents = filterEvents(analysis.getEvents());
        logExtractedEvents(meeting.getId(), filteredEvents);

        saveTasks(meeting.getId(), filteredTasks);
        saveEvents(meeting.getId(), filteredEvents);

        if (transcript.getRecordingId() != null) {
            recordingRepository.findById(transcript.getRecordingId())
                    .ifPresent(r -> r.setStatus(RecordingStatus.DONE));
        }

        transcriptRepository.save(transcript);

        return new GeminiAnalyzeResponse(
                analysis.getSummary(),
                normalizeKeywords(analysis.getKeywords()),
                filteredTasks.size(),
                filteredEvents.size()
        );
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private void saveTasks(Long meetingId, List<GeminiAnalysisResult.ExtractedTask> tasks) {
        for (GeminiAnalysisResult.ExtractedTask extractedTask : tasks) {
            Task task = new Task();
            task.setMeetingId(meetingId);
            task.setAssigneeName(extractedTask.getAssigneeName());
            task.setTitle(extractedTask.getTitle());
            task.setDescription(normalizeText(extractedTask.getDescription()));
            task.setSource(TaskSource.AI_GENERATED);
            task.setStatus(TaskStatus.TODO);

            if (extractedTask.getDueDate() != null && !extractedTask.getDueDate().isBlank()) {
                try {
                    task.setDueDate(LocalDateTime.parse(extractedTask.getDueDate(), DT_FMT));
                } catch (Exception ignored) {
                    log.warn("Invalid task due date format. value={}", extractedTask.getDueDate());
                }
            }

            taskRepository.save(task);
        }
    }

    private void saveEvents(Long meetingId, List<GeminiAnalysisResult.ExtractedEvent> events) {
        for (GeminiAnalysisResult.ExtractedEvent extractedEvent : events) {
            LocalDateTime startAt;
            LocalDateTime endAt;

            try {
                startAt = LocalDateTime.parse(extractedEvent.getStartAt(), DT_FMT);
                endAt = LocalDateTime.parse(extractedEvent.getEndAt(), DT_FMT);
            } catch (Exception ignored) {
                log.warn("Invalid event datetime format. startAt={}, endAt={}",
                        extractedEvent.getStartAt(), extractedEvent.getEndAt());
                continue;
            }

            if (endAt.isBefore(startAt)) {
                log.warn("Event endAt is before startAt. title={}, startAt={}, endAt={}",
                        extractedEvent.getTitle(), extractedEvent.getStartAt(), extractedEvent.getEndAt());
                continue;
            }

            Event event = new Event();
            event.setMeetingId(meetingId);
            event.setCreatedBy(extractedEvent.getUserId());
            event.setCreatedByName(extractedEvent.getCreatedByName());
            event.setTitle(extractedEvent.getTitle());
            event.setDescription(normalizeText(extractedEvent.getDescription()));
            event.setLocation(normalizeText(extractedEvent.getLocation()));
            event.setStartAt(startAt);
            event.setEndAt(endAt);
            event.setIsAllDay(Boolean.TRUE.equals(extractedEvent.getIsAllDay()));
            event.setColor("blue");

            for (Long participantUserId : resolveParticipantUserIds(extractedEvent)) {
                EventParticipant participant = new EventParticipant();
                participant.setEvent(event);
                participant.setUserId(participantUserId);
                participant.setStatus(ParticipantStatus.PENDING);
                event.getParticipants().add(participant);
            }

            eventRepository.save(event);
        }
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        List<String> normalized = new ArrayList<>();
        if (keywords == null) return normalized;
        for (String keyword : keywords) {
            String value = normalizeText(keyword);
            if (value == null || isPlaceholder(value) || normalized.contains(value)) continue;
            normalized.add(value);
        }
        return normalized;
    }

    private List<GeminiAnalysisResult.ExtractedTask> filterTasks(List<GeminiAnalysisResult.ExtractedTask> tasks) {
        List<GeminiAnalysisResult.ExtractedTask> filtered = new ArrayList<>();
        if (tasks == null) return filtered;
        for (GeminiAnalysisResult.ExtractedTask task : tasks) {
            if (task == null) continue;
            String title = normalizeText(task.getTitle());
            String description = normalizeText(task.getDescription());
            if (title == null || isPlaceholder(title) || isPlaceholder(description)) continue;
            filtered.add(new GeminiAnalysisResult.ExtractedTask(
                    normalizeText(task.getSpeakerLabel()),
                    normalizeText(task.getAssigneeName()),
                    title,
                    description,
                    normalizeText(task.getDueDate())
            ));
        }
        return filtered;
    }

    private List<GeminiAnalysisResult.ExtractedEvent> filterEvents(List<GeminiAnalysisResult.ExtractedEvent> events) {
        List<GeminiAnalysisResult.ExtractedEvent> filtered = new ArrayList<>();
        if (events == null) return filtered;
        for (GeminiAnalysisResult.ExtractedEvent event : events) {
            if (event == null) continue;
            String title = normalizeText(event.getTitle());
            String startAt = normalizeText(event.getStartAt());
            String endAt = normalizeText(event.getEndAt());
            if (title == null || startAt == null || endAt == null) continue;
            filtered.add(new GeminiAnalysisResult.ExtractedEvent(
                    normalizeText(event.getSpeakerLabel()),
                    event.getUserId(),
                    normalizeText(event.getCreatedByName()),
                    deduplicateParticipantUserIds(event.getParticipantUserIds()),
                    title,
                    normalizeText(event.getDescription()),
                    normalizeText(event.getLocation()),
                    startAt,
                    endAt,
                    event.getIsAllDay()
            ));
        }
        return filtered;
    }

    private LocalDate resolveMeetingDate(Meeting meeting) {
        if (meeting.getCreatedAt() != null) return meeting.getCreatedAt().toLocalDate();
        return LocalDate.now();
    }

    private void logExtractedEvents(Long meetingId, List<GeminiAnalysisResult.ExtractedEvent> events) {
        try {
            log.info("Extracted events. meetingId={}, count={}, events={}",
                    meetingId,
                    events == null ? 0 : events.size(),
                    objectMapper.writeValueAsString(events == null ? List.of() : events));
        } catch (Exception e) {
            log.warn("Failed to serialize extracted events. meetingId={}", meetingId, e);
        }
    }

    private List<Long> deduplicateParticipantUserIds(List<Long> participantUserIds) {
        List<Long> deduplicated = new ArrayList<>();
        if (participantUserIds == null) return deduplicated;
        for (Long id : participantUserIds) {
            if (id == null || deduplicated.contains(id)) continue;
            deduplicated.add(id);
        }
        return deduplicated;
    }

    private List<Long> resolveParticipantUserIds(GeminiAnalysisResult.ExtractedEvent event) {
        Set<Long> ids = new LinkedHashSet<>(deduplicateParticipantUserIds(event.getParticipantUserIds()));
        if (event.getUserId() != null) ids.add(event.getUserId());
        return new ArrayList<>(ids);
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isPlaceholder(String value) {
        if (value == null) return false;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("null") || normalized.equals("none") || normalized.equals("n/a")
                || normalized.equals("없음") || normalized.equals("미정") || normalized.equals("unknown")
                || normalized.equals("tbd");
    }
}
