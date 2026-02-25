package com.capston.demo.domain.ai.service;

import com.capston.demo.domain.ai.dto.AssemblyAiTranscriptResult;
import com.capston.demo.domain.ai.dto.ClaudeAnalysisResult;
import com.capston.demo.domain.calender.entity.*;
import com.capston.demo.domain.calender.repository.EventRepository;
import com.capston.demo.domain.calender.repository.TaskRepository;
import com.capston.demo.domain.meeting.entity.*;
import com.capston.demo.domain.meeting.repository.*;
import com.capston.demo.domain.recording.service.RecordingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j @Service @RequiredArgsConstructor
public class MeetingAnalysisService {
    private final AssemblyAiService assemblyAiService;
    private final ClaudeAiService claudeAiService;
    private final MeetingRepository meetingRepository;
    private final MeetingRecordingRepository recordingRepository;
    private final RecordingService recordingService;
    private final MeetingTranscriptRepository transcriptRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final SpeakerMappingRepository speakerMappingRepository;
    private final TaskRepository taskRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Transactional
    public void analyze(Long meetingId, Long recordingId, List<ClaudeAnalysisResult.SpeakerInfo> speakerInfos) {
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow(() -> new IllegalArgumentException("회의 없음: " + meetingId));
        MeetingRecording recording = recordingRepository.findById(recordingId).orElseThrow(() -> new IllegalArgumentException("녹음 없음: " + recordingId));
        String audioUrl = recordingService.generateDownloadPresignedUrl(recordingId).getPresignedUrl();
        AssemblyAiTranscriptResult stt = assemblyAiService.transcribe(audioUrl);
        ClaudeAnalysisResult claude = claudeAiService.analyze(stt, speakerInfos);
        MeetingTranscript transcript = saveTranscript(meeting, recording, stt, claude);
        saveSpeakerMappings(transcript, speakerInfos);
        saveTasks(meetingId, meeting.getWorkspaceId(), claude.getTasks());
        saveEvents(meetingId, meeting.getWorkspaceId(), claude.getEvents());
        recording.setStatus(RecordingStatus.DONE);
    }

    private MeetingTranscript saveTranscript(Meeting meeting, MeetingRecording recording, AssemblyAiTranscriptResult stt, ClaudeAnalysisResult claude) {
        MeetingTranscript t = new MeetingTranscript();
        t.setMeeting(meeting); t.setRecording(recording); t.setFullText(stt.getFullText());
        t.setSummary(claude.getSummary()); t.setAnalyzedAt(LocalDateTime.now());
        try { t.setKeywords(objectMapper.writeValueAsString(claude.getKeywords())); } catch (Exception e) { t.setKeywords("[]"); }
        MeetingTranscript saved = transcriptRepository.save(t);
        int seq = 0;
        for (AssemblyAiTranscriptResult.Utterance u : stt.getUtterances()) {
            TranscriptSegment seg = new TranscriptSegment();
            seg.setTranscript(saved); seg.setSpeakerLabel(u.getSpeaker()); seg.setContent(u.getText());
            seg.setStartSec((float) u.getStartSec()); seg.setEndSec((float) u.getEndSec()); seg.setSequence(seq++);
            segmentRepository.save(seg);
        }
        return saved;
    }

    private void saveSpeakerMappings(MeetingTranscript transcript, List<ClaudeAnalysisResult.SpeakerInfo> infos) {
        for (ClaudeAnalysisResult.SpeakerInfo info : infos) {
            SpeakerMapping m = new SpeakerMapping();
            m.setTranscript(transcript); m.setSpeakerLabel(info.getSpeakerLabel()); m.setUserId(info.getUserId());
            speakerMappingRepository.save(m);
        }
    }

    private void saveTasks(Long meetingId, Long workspaceId, List<ClaudeAnalysisResult.ExtractedTask> list) {
        for (ClaudeAnalysisResult.ExtractedTask et : list) {
            Task task = new Task();
            task.setMeetingId(meetingId); task.setWorkspaceId(workspaceId); task.setAssigneeId(et.getUserId());
            task.setTitle(et.getTitle()); task.setDescription(et.getDescription());
            task.setSource(TaskSource.AI_GENERATED); task.setStatus(TaskStatus.TODO);
            if (et.getDueDate() != null && !et.getDueDate().isBlank())
                try { task.setDueDate(LocalDateTime.parse(et.getDueDate(), DT_FMT)); } catch (Exception ignored) {}
            taskRepository.save(task);
        }
    }

    private void saveEvents(Long meetingId, Long workspaceId, List<ClaudeAnalysisResult.ExtractedEvent> list) {
        for (ClaudeAnalysisResult.ExtractedEvent ee : list) {
            if (ee.getStartAt() == null || ee.getEndAt() == null) continue;
            try {
                Event event = new Event();
                event.setMeetingId(meetingId); event.setWorkspaceId(workspaceId); event.setCreatedBy(ee.getUserId());
                event.setTitle(ee.getTitle()); event.setDescription(ee.getDescription()); event.setLocation(ee.getLocation());
                event.setStartAt(LocalDateTime.parse(ee.getStartAt(), DT_FMT)); event.setEndAt(LocalDateTime.parse(ee.getEndAt(), DT_FMT));
                event.setIsAllDay(false);
                eventRepository.save(event);
            } catch (Exception ignored) {}
        }
    }
}
