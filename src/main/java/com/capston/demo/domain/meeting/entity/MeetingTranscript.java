package com.capston.demo.domain.meeting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "meeting_transcripts")
@Getter
@Setter
public class MeetingTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recording_id")
    private MeetingRecording recording;

    @Column(name = "full_text", columnDefinition = "LONGTEXT")
    private String fullText;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // JSON 배열로 저장: ["예산", "마감일", ...]
    @Column(columnDefinition = "JSON")
    private String keywords;

    private LocalDateTime analyzedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "transcript", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TranscriptSegment> segments = new ArrayList<>();

    @OneToMany(mappedBy = "transcript", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SpeakerMapping> speakerMappings = new ArrayList<>();
}
