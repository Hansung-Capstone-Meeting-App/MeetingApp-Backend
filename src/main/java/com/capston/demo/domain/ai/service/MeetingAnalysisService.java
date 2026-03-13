package com.capston.demo.domain.ai.service;

import com.capston.demo.domain.ai.dto.AssemblyAiTranscriptResult;
import com.capston.demo.domain.ai.dto.GeminiAnalysisResult;
import com.capston.demo.domain.calender.entity.Task;
import com.capston.demo.domain.calender.entity.TaskSource;
import com.capston.demo.domain.calender.entity.TaskStatus;
import com.capston.demo.domain.calender.repository.TaskRepository;
import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.entity.MeetingRecording;
import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import com.capston.demo.domain.meeting.entity.RecordingStatus;
import com.capston.demo.domain.meeting.entity.SpeakerMapping;
import com.capston.demo.domain.meeting.entity.TranscriptSegment;
import com.capston.demo.domain.meeting.repository.MeetingRecordingRepository;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.meeting.repository.MeetingTranscriptRepository;
import com.capston.demo.domain.meeting.repository.SpeakerMappingRepository;
import com.capston.demo.domain.meeting.repository.TranscriptSegmentRepository;
import com.capston.demo.domain.recording.service.RecordingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private final MeetingTranscriptRepository transcriptRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final SpeakerMappingRepository speakerMappingRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void analyze(Long meetingId, Long recordingId, List<GeminiAnalysisResult.SpeakerInfo> speakerInfos) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId));
        MeetingRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new IllegalArgumentException("녹음 파일을 찾을 수 없습니다. id=" + recordingId));

        String audioUrl = recordingService.generateDownloadPresignedUrl(recordingId).getPresignedUrl();
        AssemblyAiTranscriptResult stt = assemblyAiService.transcribe(audioUrl);
        GeminiAnalysisResult analysis = geminiAiService.analyze(stt, speakerInfos);

        MeetingTranscript transcript = saveTranscript(meeting, recording, stt, analysis);
        saveSpeakerMappings(transcript, speakerInfos);
        saveTasks(meetingId, meeting.getWorkspaceId(), filterTasks(analysis.getTasks()));

        // 일정 자동 생성은 아직 미구현 상태라 저장하지 않는다.
        if (analysis.getEvents() != null && !analysis.getEvents().isEmpty()) {
            log.info("Meeting events were extracted but event persistence is disabled until schedule creation is implemented. meetingId={}", meetingId);
        }

        recording.setStatus(RecordingStatus.DONE);
    }

    private MeetingTranscript saveTranscript(
            Meeting meeting,
            MeetingRecording recording,
            AssemblyAiTranscriptResult stt,
            GeminiAnalysisResult analysis
    ) {
        MeetingTranscript transcript = new MeetingTranscript();
        transcript.setMeeting(meeting);
        transcript.setRecording(recording);
        transcript.setFullText(stt.getFullText());
        transcript.setSummary(normalizeText(analysis.getSummary()));
        transcript.setAnalyzedAt(LocalDateTime.now());

        try {
            transcript.setKeywords(objectMapper.writeValueAsString(normalizeKeywords(analysis.getKeywords())));
        } catch (Exception e) {
            transcript.setKeywords("[]");
        }

        MeetingTranscript saved = transcriptRepository.save(transcript);

        int sequence = 0;
        for (AssemblyAiTranscriptResult.Utterance utterance : stt.getUtterances()) {
            TranscriptSegment segment = new TranscriptSegment();
            segment.setTranscript(saved);
            segment.setSpeakerLabel(utterance.getSpeaker());
            segment.setContent(utterance.getText());
            segment.setStartSec((float) utterance.getStartSec());
            segment.setEndSec((float) utterance.getEndSec());
            segment.setSequence(sequence++);
            segmentRepository.save(segment);
        }

        return saved;
    }

    private void saveSpeakerMappings(MeetingTranscript transcript, List<GeminiAnalysisResult.SpeakerInfo> infos) {
        for (GeminiAnalysisResult.SpeakerInfo info : infos) {
            SpeakerMapping mapping = new SpeakerMapping();
            mapping.setTranscript(transcript);
            mapping.setSpeakerLabel(info.getSpeakerLabel());
            mapping.setUserId(info.getUserId());
            speakerMappingRepository.save(mapping);
        }
    }

    private void saveTasks(Long meetingId, Long workspaceId, List<GeminiAnalysisResult.ExtractedTask> tasks) {
        for (GeminiAnalysisResult.ExtractedTask extractedTask : tasks) {
            Task task = new Task();
            task.setMeetingId(meetingId);
            task.setWorkspaceId(workspaceId);
            task.setAssigneeId(extractedTask.getUserId());
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

    private List<String> normalizeKeywords(List<String> keywords) {
        List<String> normalized = new ArrayList<>();
        if (keywords == null) {
            return normalized;
        }

        for (String keyword : keywords) {
            String value = normalizeText(keyword);
            if (value == null || isPlaceholder(value) || normalized.contains(value)) {
                continue;
            }
            normalized.add(value);
        }
        return normalized;
    }

    private List<GeminiAnalysisResult.ExtractedTask> filterTasks(List<GeminiAnalysisResult.ExtractedTask> tasks) {
        List<GeminiAnalysisResult.ExtractedTask> filtered = new ArrayList<>();
        if (tasks == null) {
            return filtered;
        }

        for (GeminiAnalysisResult.ExtractedTask task : tasks) {
            if (task == null) {
                continue;
            }

            String title = normalizeText(task.getTitle());
            String description = normalizeText(task.getDescription());
            if (title == null || isPlaceholder(title) || isPlaceholder(description)) {
                continue;
            }

            filtered.add(new GeminiAnalysisResult.ExtractedTask(
                    normalizeText(task.getSpeakerLabel()),
                    task.getUserId(),
                    title,
                    description,
                    normalizeText(task.getDueDate())
            ));
        }

        return filtered;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private boolean isPlaceholder(String value) {
        if (value == null) {
            return false;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("null")
                || normalized.equals("none")
                || normalized.equals("n/a")
                || normalized.equals("없음")
                || normalized.equals("미정")
                || normalized.equals("unknown")
                || normalized.equals("tbd");
    }
}
