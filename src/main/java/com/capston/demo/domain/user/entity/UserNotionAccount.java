package com.capston.demo.domain.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_notion_accounts")
@Getter
@Setter
@NoArgsConstructor
public class UserNotionAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 우리 서비스의 User와 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Notion의 사용자 ID
    @Column(name = "notion_user_id", nullable = false, length = 255)
    private String notionUserId;

    // Notion 워크스페이스/계정 구분용 이름(선택)
    @Column(name = "notion_name", length = 255)
    private String notionName;

    // 현재 사용 중인 Notion 액세스 토큰
    @Column(name = "access_token", nullable = false, length = 4000)
    private String accessToken;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt = LocalDateTime.now();

    /** 캘린더 동기화 대상 노션 데이터베이스 ID (유저가 URL로 한 번 등록) */
    @Column(name = "calendar_database_id", length = 100)
    private String calendarDatabaseId;

    // 나중에 여러 Notion 계정을 연결할 수 있게 하려면 isPrimary 같은 플래그도 둘 수 있음
}

