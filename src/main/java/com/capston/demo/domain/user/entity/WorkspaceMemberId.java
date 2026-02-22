package com.capston.demo.domain.user.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.*;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode // 복합키는 동등성 비교가 필수입니다!
public class WorkspaceMemberId implements Serializable {

    private Long workspaceId;
    private Long userId;
}