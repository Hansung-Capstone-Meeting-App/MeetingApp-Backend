package com.capston.demo.domain.user.repository;

import com.capston.demo.domain.user.entity.WorkspaceMember;
import com.capston.demo.domain.user.entity.WorkspaceMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {

    boolean existsByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);

    List<WorkspaceMember> findByWorkspace_Id(Long workspaceId);
}

