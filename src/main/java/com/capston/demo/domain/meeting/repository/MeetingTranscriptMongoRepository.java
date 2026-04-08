package com.capston.demo.domain.meeting.repository;

import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingTranscriptMongoRepository extends MongoRepository<MeetingTranscript, String> {

    Optional<MeetingTranscript> findByMeetingId(Long meetingId);

    List<MeetingTranscript> findByMeetingIdOrderByCreatedAtDesc(Long meetingId);
}
