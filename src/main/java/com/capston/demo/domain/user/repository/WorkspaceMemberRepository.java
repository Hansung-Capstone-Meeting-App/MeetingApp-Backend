package com.capston.demo.domain.user.repository;

import com.capston.demo.domain.user.entity.WorkspaceMember;
import com.capston.demo.domain.user.entity.WorkspaceMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {

    /**
     * 주어진 워크스페이스에 해당 유저가 멤버로 속해있는지 여부를 확인한다.
     */
    boolean existsByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);
}

