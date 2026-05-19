package com.capston.demo.domain.meeting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "meetings")
@Getter
@Setter
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String title;

    private Long createdBy;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Notion 회의록 DB에 생성된 페이지 ID (재전송 시 갱신) */
    @Column(name = "notion_page_id", length = 100)
    private String notionPageId;

    @Column(name = "notion_exported_at")
    private LocalDateTime notionExportedAt;

    @OneToMany(mappedBy = "meeting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MeetingRecording> recordings = new ArrayList<>();

}
