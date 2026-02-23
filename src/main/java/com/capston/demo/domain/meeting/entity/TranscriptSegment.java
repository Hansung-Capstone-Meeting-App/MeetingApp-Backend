package com.capston.demo.domain.meeting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "transcript_segments")
@Getter
@Setter
public class TranscriptSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transcript_id")
    private MeetingTranscript transcript;

    @Column(name = "speaker_label", length = 50)
    private String speakerLabel;

    // 화자 매핑 후 실제 유저 ID (nullable)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Float startSec;

    private Float endSec;

    @Column(name = "sequence_order")
    private Integer sequence;
}
