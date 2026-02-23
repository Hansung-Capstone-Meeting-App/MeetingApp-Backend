package com.capston.demo.domain.meeting.service;

import com.capston.demo.domain.meeting.dto.request.MeetingRequest;
import com.capston.demo.domain.meeting.dto.response.MeetingResponse;
import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;

    @Transactional
    public MeetingResponse startMeeting(MeetingRequest request) {
        Meeting meeting = new Meeting();
        meeting.setWorkspaceId(request.getWorkspaceId());
        meeting.setChannelId(request.getChannelId());
        meeting.setTitle(request.getTitle());
        meeting.setCreatedBy(request.getCreatedBy());
        meeting.setStartedAt(LocalDateTime.now());
        return new MeetingResponse(meetingRepository.save(meeting));
    }

    @Transactional
    public MeetingResponse endMeeting(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId));
        meeting.setEndedAt(LocalDateTime.now());
        return new MeetingResponse(meeting);
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId));
        return new MeetingResponse(meeting);
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> getMeetings(Long workspaceId, Long channelId) {
        List<Meeting> meetings;
        if (workspaceId != null && channelId != null) {
            meetings = meetingRepository.findByWorkspaceIdAndChannelIdOrderByCreatedAtDesc(workspaceId, channelId);
        } else if (workspaceId != null) {
            meetings = meetingRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        } else if (channelId != null) {
            meetings = meetingRepository.findByChannelIdOrderByCreatedAtDesc(channelId);
        } else {
            meetings = meetingRepository.findAll();
        }
        return meetings.stream().map(MeetingResponse::new).collect(Collectors.toList());
    }

    @Transactional
    public void deleteMeeting(Long meetingId) {
        meetingRepository.deleteById(meetingId);
    }
}
