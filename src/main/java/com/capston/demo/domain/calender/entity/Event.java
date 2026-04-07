package com.capston.demo.domain.calender.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Getter
@Setter
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이벤트가 속한 워크스페이스(팀/프로젝트)의 식별자
    private Long workspaceId; // 연관관계 매핑 시 Workspace 엔티티로 변경 가능

    // 이벤트 제목 (노션 캘린더의 제목과 매핑)
    @Column(nullable = false, length = 500)
    private String title;

    // 이벤트에 대한 상세 설명
    @Column(columnDefinition = "TEXT")
    private String description;

    // 이벤트가 열리는 장소(회의실, 링크 등)
    @Column(length = 500)
    private String location;

    // 이벤트 시작 일시
    @Column(nullable = false)
    private LocalDateTime startAt;

    // 이벤트 종료 일시
    @Column(nullable = false)
    private LocalDateTime endAt;

    // 하루 종일 이벤트 여부 (true면 시간 정보 대신 날짜만 의미)
    private Boolean isAllDay = false;

    // 이벤트를 생성한 사용자 ID (User와 연관관계로 확장 가능)
    private Long createdBy;

    // 이벤트를 생성한 사용자 이름 (메신저 연동 전 직접 입력)
    @Column(name = "created_by_name", length = 100)
    private String createdByName;

    // 관련 회의(미팅) 엔티티와 연결하기 위한 식별자
    private Long meetingId; // 회의 도메인과 연결

    // 캘린더에서 이 이벤트를 표시할 색상값(예: HEX 코드, 프리셋 키 등)
    @Column(length = 20)
    private String color;

    // 이벤트가 생성된 시각
    private LocalDateTime createdAt = LocalDateTime.now();

    // 이 이벤트에 참여하는 사용자 목록 (참여자 도메인과의 1:N 관계)
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    private List<EventParticipant> participants = new ArrayList<>();
}