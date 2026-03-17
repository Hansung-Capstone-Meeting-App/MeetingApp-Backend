package com.capston.demo.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember {

    @EmbeddedId
    private WorkspaceMemberId id;

    @MapsId("workspaceId") // WorkspaceMemberId 내부의 workspaceId와 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @MapsId("userId") // WorkspaceMemberId 내부의 userId와 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private MemberRole role = MemberRole.member;

    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    public enum MemberRole {
        owner, admin, member
    }

    /** 테스트/개발용: 워크스페이스와 유저로 멤버 생성 (워크스페이스는 이미 저장된 상태여야 함) */
    public WorkspaceMember(Workspace workspace, User user) {
        this.id = new WorkspaceMemberId(workspace.getId(), user.getId());
        this.workspace = workspace;
        this.user = user;
    }
}
