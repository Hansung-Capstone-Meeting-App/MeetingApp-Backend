package com.capston.demo.domain.meeting.service;

import com.capston.demo.domain.meeting.dto.response.MeetingResponse;
import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(Long meetingId, Long userId) {
        Meeting meeting = meetingRepository.findByIdAndCreatedBy(meetingId, userId)
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId));
        return new MeetingResponse(meeting);
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> getMeetings(Long createdBy) {
        return meetingRepository.findByCreatedByOrderByCreatedAtDesc(createdBy)
                .stream().map(MeetingResponse::new).collect(Collectors.toList());
    }

    @Transactional
    public void deleteMeeting(Long meetingId, Long userId) {
        Meeting meeting = meetingRepository.findByIdAndCreatedBy(meetingId, userId)
                .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId));
        meetingRepository.delete(meeting);
    }
}
