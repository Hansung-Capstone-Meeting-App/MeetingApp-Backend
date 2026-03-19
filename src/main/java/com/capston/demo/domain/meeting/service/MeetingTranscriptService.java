package com.capston.demo.domain.meeting.service;

import com.capston.demo.domain.meeting.dto.request.SpeakerMappingRequest;
import com.capston.demo.domain.meeting.dto.request.TranscriptRequest;
import com.capston.demo.domain.meeting.dto.response.SpeakerMappingResponse;
import com.capston.demo.domain.meeting.dto.response.TranscriptResponse;
import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.entity.MeetingRecording;
import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import com.capston.demo.domain.meeting.entity.SpeakerMapping;
import com.capston.demo.domain.meeting.entity.TranscriptSegment;
import com.capston.demo.domain.meeting.repository.MeetingRecordingRepository;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.meeting.repository.MeetingTranscriptRepository;
import com.capston.demo.domain.meeting.repository.SpeakerMappingRepository;
import com.capston.demo.domain.meeting.repository.TranscriptSegmentRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeetingTranscriptService {

    private final MeetingRepository meetingRepository;
    private final MeetingRecordingRepository recordingRepository;
    private final MeetingTranscriptRepository transcriptRepository;
    private final TranscriptSegmentRepository segmentRepository;
    private final SpeakerMappingRepository speakerMappingRepository;

    @Transactional
    public TranscriptResponse saveTranscript(Long meetingId, TranscriptRequest request) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId));

        MeetingTranscript transcript = new MeetingTranscript();
        transcript.setMeeting(meeting);
        transcript.setFullText(request.getFullText());
        transcript.setSummary(request.getSummary());
        transcript.setKeywords(request.getKeywords());
        transcript.setAnalyzedAt(LocalDateTime.now());

        if (request.getRecordingId() != null) {
            MeetingRecording recording = recordingRepository.findById(request.getRecordingId())
                    .orElseThrow(() -> new IllegalArgumentException("녹음 파일을 찾을 수 없습니다. id=" + request.getRecordingId()));
            transcript.setRecording(recording);
        }

        MeetingTranscript saved = transcriptRepository.save(transcript);

        if (request.getSegments() != null) {
            List<TranscriptSegment> segments = new ArrayList<>();
            for (TranscriptRequest.SegmentRequest segReq : request.getSegments()) {
                TranscriptSegment segment = new TranscriptSegment();
                segment.setTranscript(saved);
                segment.setSpeakerLabel(segReq.getSpeakerLabel());
                segment.setUserId(segReq.getUserId());
                segment.setContent(segReq.getContent());
                segment.setStartSec(segReq.getStartSec());
                segment.setEndSec(segReq.getEndSec());
                segment.setSequence(segReq.getSequence());
                segments.add(segment);
            }
            segmentRepository.saveAll(segments);
            saved.setSegments(segments);
        }

        return new TranscriptResponse(saved);
    }

    @Transactional(readOnly = true)
    public TranscriptResponse getTranscript(Long meetingId, Long userId) {
        meetingRepository.findByIdAndCreatedBy(meetingId, userId)
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId));
        MeetingTranscript transcript = transcriptRepository.findByMeetingIdOrderByCreatedAtDesc(meetingId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("트랜스크립트가 없습니다. meetingId=" + meetingId));
        return new TranscriptResponse(transcript);
    }

    @Transactional
    public List<SpeakerMappingResponse> saveSpeakerMappings(Long transcriptId, SpeakerMappingRequest request) {
        MeetingTranscript transcript = transcriptRepository.findById(transcriptId)
                .orElseThrow(() -> new IllegalArgumentException("트랜스크립트를 찾을 수 없습니다. id=" + transcriptId));

        List<SpeakerMapping> result = new ArrayList<>();

        for (SpeakerMappingRequest.MappingItem item : request.getMappings()) {
            SpeakerMapping mapping = speakerMappingRepository
                    .findByTranscriptIdAndSpeakerLabel(transcriptId, item.getSpeakerLabel())
                    .orElseGet(() -> {
                        SpeakerMapping newMapping = new SpeakerMapping();
                        newMapping.setTranscript(transcript);
                        newMapping.setSpeakerLabel(item.getSpeakerLabel());
                        return newMapping;
                    });

            mapping.setUserId(item.getUserId());
            result.add(speakerMappingRepository.save(mapping));

            List<TranscriptSegment> segments = segmentRepository
                    .findByTranscriptIdAndSpeakerLabel(transcriptId, item.getSpeakerLabel());
            segments.forEach(segment -> segment.setUserId(item.getUserId()));
            segmentRepository.saveAll(segments);
        }

        return result.stream()
                .map(SpeakerMappingResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SpeakerMappingResponse> getSpeakerMappings(Long transcriptId) {
        return speakerMappingRepository.findByTranscriptId(transcriptId).stream()
                .map(SpeakerMappingResponse::new)
                .collect(Collectors.toList());
    }
}
