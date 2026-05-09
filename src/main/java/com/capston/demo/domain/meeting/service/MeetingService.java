package com.capston.demo.domain.meeting.service;

import com.capston.demo.domain.calender.entity.TaskStatus;
import com.capston.demo.domain.calender.repository.EventRepository;
import com.capston.demo.domain.calender.repository.TaskRepository;
import com.capston.demo.domain.meeting.dto.request.MeetingRequest;
import com.capston.demo.domain.meeting.dto.response.MeetingResponse;
import com.capston.demo.domain.meeting.dto.response.MeetingSummaryResponse;
import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.meeting.repository.MeetingTranscriptMongoRepository;
import com.capston.demo.domain.user.repository.WorkspaceMemberRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final MeetingTranscriptMongoRepository transcriptRepository;
    private final TaskRepository taskRepository;
    private final EventRepository eventRepository;

    @Transactional
    public MeetingResponse createMeeting(MeetingRequest request, Long userId) {
        if (request.getWorkspaceId() == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_ID_REQUIRED);
        }
        if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(request.getWorkspaceId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        Meeting meeting = new Meeting();
        meeting.setWorkspaceId(request.getWorkspaceId());
        meeting.setTitle(request.getTitle());
        meeting.setCreatedBy(userId);
        return new MeetingResponse(meetingRepository.save(meeting));
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(Long meetingId, Long userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        checkAccess(meeting, userId);
        return new MeetingResponse(meeting);
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> getMeetingsByWorkspace(Long workspaceId, Long userId) {
        if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, userId)) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        return meetingRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .stream().map(MeetingResponse::new).collect(Collectors.toList());
    }

    // Slack이 생성한 회의(workspaceId 없음) 조회용 — 생성자 본인만 가능
    @Transactional(readOnly = true)
    public List<MeetingResponse> getMeetings(Long createdBy) {
        return meetingRepository.findByCreatedByOrderByCreatedAtDesc(createdBy)
                .stream().map(MeetingResponse::new).collect(Collectors.toList());
    }

    @Transactional
    public void deleteMeeting(Long meetingId, Long userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        checkAccess(meeting, userId);
        meetingRepository.delete(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingSummaryResponse getMeetingSummary(Long meetingId, Long userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        checkAccess(meeting, userId);

        MeetingTranscript transcript = transcriptRepository
                .findByMeetingIdOrderByCreatedAtDesc(meetingId)
                .stream().findFirst().orElse(null);

        Map<TaskStatus, Long> taskCountMap = taskRepository.findByMeetingId(meetingId).stream()
                .collect(Collectors.groupingBy(t -> t.getStatus() != null ? t.getStatus() : TaskStatus.TODO,
                        Collectors.counting()));

        long eventCount = eventRepository.countByMeetingId(meetingId);

        return new MeetingSummaryResponse(
                meetingId,
                meeting.getTitle(),
                transcript != null ? transcript.getSummary() : null,
                transcript != null ? transcript.getKeywords() : List.of(),
                transcript != null ? transcript.getAnalyzedAt() : null,
                taskCountMap,
                eventCount
        );
    }

    // workspaceId 있으면 멤버십 체크, 없으면(Slack 생성) createdBy 체크
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
