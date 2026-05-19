package com.capston.demo.domain.recording.service;

import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.entity.MeetingRecording;
import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import com.capston.demo.domain.meeting.entity.RecordingStatus;
import com.capston.demo.domain.meeting.repository.MeetingRecordingRepository;
import com.capston.demo.domain.meeting.repository.MeetingTranscriptMongoRepository;
import com.capston.demo.domain.recording.dto.response.RecordingPipelinePhase;
import com.capston.demo.domain.recording.dto.response.RecordingPipelineResponse;
import com.capston.demo.domain.user.repository.WorkspaceMemberRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecordingPipelineService {

    private final MeetingRecordingRepository recordingRepository;
    private final MeetingTranscriptMongoRepository transcriptRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional(readOnly = true)
    public RecordingPipelineResponse getPipeline(Long recordingId, Long userId) {
        MeetingRecording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORDING_NOT_FOUND));
        checkAccess(recording.getMeeting(), userId);

        MeetingTranscript transcript = transcriptRepository.findTopByRecordingIdOrderByCreatedAtDesc(recordingId)
                .orElse(null);

        boolean mappingComplete = transcript != null && isMappingComplete(transcript);
        boolean analysisComplete = transcript != null && transcript.getAnalyzedAt() != null;

        RecordingPipelinePhase phase = resolvePhase(recording.getStatus(), transcript, mappingComplete, analysisComplete);

        String transcriptId = transcript != null ? transcript.getId() : null;

        return new RecordingPipelineResponse(recording, phase, transcriptId, mappingComplete, analysisComplete, null);
    }

    private RecordingPipelinePhase resolvePhase(
            RecordingStatus recordingStatus,
            MeetingTranscript transcript,
            boolean mappingComplete,
            boolean analysisComplete) {

        if (recordingStatus == RecordingStatus.FAILED) {
            return RecordingPipelinePhase.FAILED;
        }
        if (analysisComplete) {
            return RecordingPipelinePhase.COMPLETE;
        }
        if (recordingStatus == RecordingStatus.DONE) {
            return RecordingPipelinePhase.COMPLETE;
        }

        boolean hasTranscript = transcript != null;

        if (recordingStatus == RecordingStatus.PROCESSING && !hasTranscript) {
            return RecordingPipelinePhase.TRANSCRIBING;
        }

        if (hasTranscript) {
            if (!mappingComplete) {
                return RecordingPipelinePhase.AWAITING_SPEAKER_MAPPING;
            }
            return RecordingPipelinePhase.READY_FOR_ANALYSIS;
        }

        if (recordingStatus == RecordingStatus.UPLOADING) {
            return RecordingPipelinePhase.UPLOADING;
        }

        return RecordingPipelinePhase.UPLOADED;
    }

    private boolean isMappingComplete(MeetingTranscript t) {
        if (t.getSegments() == null || t.getSegments().isEmpty()) {
            return true;
        }
        Set<String> labels = t.getSegments().stream()
                .map(MeetingTranscript.SegmentEmbedded::getSpeakerLabel)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (labels.isEmpty()) {
            return true;
        }

        Map<String, MeetingTranscript.SpeakerMappingEmbedded> byLabel =
                Optional.ofNullable(t.getSpeakerMappings()).orElseGet(Collections::emptyList)
                        .stream()
                        .filter(m -> m.getSpeakerLabel() != null)
                        .collect(Collectors.toMap(
                                MeetingTranscript.SpeakerMappingEmbedded::getSpeakerLabel,
                                Function.identity(),
                                (a, b) -> a
                        ));

        for (String label : labels) {
            MeetingTranscript.SpeakerMappingEmbedded m = byLabel.get(label);
            if (m == null || m.getUserId() == null) {
                return false;
            }
        }
        return true;
    }

    private void checkAccess(Meeting meeting, Long userId) {
        if (meeting.getWorkspaceId() != null) {
            if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(meeting.getWorkspaceId(), userId)) {
                throw new BusinessException(ErrorCode.MEETING_ACCESS_DENIED);
            }
        } else {
            if (!userId.equals(meeting.getCreatedBy())) {
                throw new BusinessException(ErrorCode.MEETING_ACCESS_DENIED);
            }
        }
    }
}
