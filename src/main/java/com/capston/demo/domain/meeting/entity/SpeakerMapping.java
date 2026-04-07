package com.capston.demo.domain.meeting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "speaker_mappings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"transcript_id", "speaker_label"})
)
@Getter
@Setter
public class SpeakerMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transcript_id")
    private MeetingTranscript transcript;

    @Column(name = "speaker_label", length = 50)
    private String speakerLabel;

    private Long userId;

    @Column(name = "user_name", length = 100)
    private String userName;
}
