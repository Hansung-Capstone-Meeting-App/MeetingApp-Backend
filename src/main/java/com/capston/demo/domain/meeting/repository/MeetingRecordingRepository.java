package com.capston.demo.domain.meeting.repository;

import com.capston.demo.domain.meeting.entity.MeetingRecording;
import com.capston.demo.domain.meeting.entity.RecordingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRecordingRepository extends JpaRepository<MeetingRecording, Long> {

    List<MeetingRecording> findByMeetingId(Long meetingId);

    List<MeetingRecording> findByMeetingIdAndStatus(Long meetingId, RecordingStatus status);
}
