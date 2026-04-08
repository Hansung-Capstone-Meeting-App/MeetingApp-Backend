package com.capston.demo.domain.meeting.service;

import com.capston.demo.domain.meeting.dto.request.SpeakerMappingRequest;
import com.capston.demo.domain.meeting.dto.request.TranscriptRequest;
import com.capston.demo.domain.meeting.dto.response.SpeakerMappingResponse;
import com.capston.demo.domain.meeting.dto.response.TranscriptResponse;
import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.meeting.repository.MeetingTranscriptMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingTranscriptService {

    private final MeetingRepository meetingRepository;
    private final MeetingTranscriptMongoRepository transcriptRepository;

    public TranscriptResponse saveTranscript(Long meetingId, TranscriptRequest request) {
        MeetingTranscript transcript = new MeetingTranscript();
        transcript.setMeetingId(meetingId);
        transcript.setFullText(request.getFullText());
        transcript.setSummary(request.getSummary());
        transcript.setAnalyzedAt(LocalDateTime.now());

        if (request.getRecordingId() != null) {
            transcript.setRecordingId(request.getRecordingId());
        }

        if (request.getKeywords() != null) {
            transcript.setKeywords(parseKeywords(request.getKeywords()));
        }

        if (request.getSegments() != null) {
            List<MeetingTranscript.SegmentEmbedded> segments = new ArrayList<>();
            for (TranscriptRequest.SegmentRequest segReq : request.getSegments()) {
                MeetingTranscript.SegmentEmbedded segment = new MeetingTranscript.SegmentEmbedded();
                segment.setSpeakerLabel(segReq.getSpeakerLabel());
                segment.setUserId(segReq.getUserId());
                segment.setContent(segReq.getContent());
                segment.setStartSec(segReq.getStartSec());
                segment.setEndSec(segReq.getEndSec());
                segment.setSequence(segReq.getSequence());
                segments.add(segment);
            }
            transcript.setSegments(segments);
        }

        return new TranscriptResponse(transcriptRepository.save(transcript));
    }

    public TranscriptResponse getTranscript(Long meetingId, Long userId) {
        meetingRepository.findByIdAndCreatedBy(meetingId, userId)
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId));
        MeetingTranscript transcript = transcriptRepository.findByMeetingIdOrderByCreatedAtDesc(meetingId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("트랜스크립트가 없습니다. meetingId=" + meetingId));
        return new TranscriptResponse(transcript);
    }

    public List<SpeakerMappingResponse> saveSpeakerMappings(String transcriptId, SpeakerMappingRequest request) {
        MeetingTranscript transcript = transcriptRepository.findById(transcriptId)
                .orElseThrow(() -> new IllegalArgumentException("트랜스크립트를 찾을 수 없습니다. id=" + transcriptId));

        for (SpeakerMappingRequest.MappingItem item : request.getMappings()) {
            MeetingTranscript.SpeakerMappingEmbedded existing = transcript.getSpeakerMappings().stream()
                    .filter(m -> m.getSpeakerLabel().equals(item.getSpeakerLabel()))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                existing.setUserId(item.getUserId());
                existing.setUserName(item.getUserName());
            } else {
                MeetingTranscript.SpeakerMappingEmbedded mapping = new MeetingTranscript.SpeakerMappingEmbedded();
                mapping.setSpeakerLabel(item.getSpeakerLabel());
                mapping.setUserId(item.getUserId());
                mapping.setUserName(item.getUserName());
                transcript.getSpeakerMappings().add(mapping);
            }
        }

        transcriptRepository.save(transcript);

        return transcript.getSpeakerMappings().stream()
                .map(m -> new SpeakerMappingResponse(transcriptId, m))
                .collect(Collectors.toList());
    }

    public List<SpeakerMappingResponse> getSpeakerMappings(String transcriptId) {
        MeetingTranscript transcript = transcriptRepository.findById(transcriptId)
                .orElseThrow(() -> new IllegalArgumentException("트랜스크립트를 찾을 수 없습니다. id=" + transcriptId));
        return transcript.getSpeakerMappings().stream()
                .map(m -> new SpeakerMappingResponse(transcriptId, m))
                .collect(Collectors.toList());
    }

    // TranscriptRequest.keywords가 JSON 문자열 형태로 올 경우 파싱
    private List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) return new ArrayList<>();
        String trimmed = keywords.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.replaceAll("^\\[|\\]$", "");
        }
        if (trimmed.isBlank()) return new ArrayList<>();
        return List.of(trimmed.split(",")).stream()
                .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }
}
