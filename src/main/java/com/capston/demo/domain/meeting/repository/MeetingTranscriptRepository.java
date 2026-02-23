package com.capston.demo.domain.meeting.repository;

import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingTranscriptRepository extends JpaRepository<MeetingTranscript, Long> {

    Optional<MeetingTranscript> findByMeetingId(Long meetingId);

    List<MeetingTranscript> findByMeetingIdOrderByCreatedAtDesc(Long meetingId);
}
