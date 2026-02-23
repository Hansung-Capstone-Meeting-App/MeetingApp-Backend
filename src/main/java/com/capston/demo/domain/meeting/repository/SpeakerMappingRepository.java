package com.capston.demo.domain.meeting.repository;

import com.capston.demo.domain.meeting.entity.SpeakerMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpeakerMappingRepository extends JpaRepository<SpeakerMapping, Long> {

    List<SpeakerMapping> findByTranscriptId(Long transcriptId);

    Optional<SpeakerMapping> findByTranscriptIdAndSpeakerLabel(Long transcriptId, String speakerLabel);
}
