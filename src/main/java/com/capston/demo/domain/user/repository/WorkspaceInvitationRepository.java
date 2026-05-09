package com.capston.demo.domain.user.repository;

import com.capston.demo.domain.user.entity.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, Long> {

    List<WorkspaceInvitation> findByInvitee_IdAndStatus(Long inviteeId, WorkspaceInvitation.InvitationStatus status);

    boolean existsByWorkspace_IdAndInvitee_IdAndStatus(Long workspaceId, Long inviteeId, WorkspaceInvitation.InvitationStatus status);

    Optional<WorkspaceInvitation> findByIdAndInvitee_Id(Long id, Long inviteeId);
}
