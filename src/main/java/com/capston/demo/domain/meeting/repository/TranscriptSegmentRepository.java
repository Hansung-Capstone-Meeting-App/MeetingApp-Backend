package com.capston.demo.domain.meeting.repository;

import com.capston.demo.domain.meeting.entity.TranscriptSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranscriptSegmentRepository extends JpaRepository<TranscriptSegment, Long> {

    List<TranscriptSegment> findByTranscriptIdOrderBySequence(Long transcriptId);

    List<TranscriptSegment> findByTranscriptIdAndSpeakerLabel(Long transcriptId, String speakerLabel);
}
