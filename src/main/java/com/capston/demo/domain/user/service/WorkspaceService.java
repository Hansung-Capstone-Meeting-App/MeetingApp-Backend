package com.capston.demo.domain.user.service;

import com.capston.demo.domain.calender.repository.EventRepository;
import com.capston.demo.domain.calender.repository.TaskRepository;
import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.entity.MeetingRecording;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.meeting.repository.MeetingTranscriptMongoRepository;
import com.capston.demo.domain.user.dto.workspace.InvitationResponse;
import com.capston.demo.domain.user.dto.workspace.InvitationCountResponse;
import com.capston.demo.domain.user.dto.workspace.WorkspaceCreateRequest;
import com.capston.demo.domain.user.dto.workspace.WorkspaceInviteRequest;
import com.capston.demo.domain.user.dto.workspace.WorkspaceUpdateRequest;
import com.capston.demo.domain.user.dto.workspace.WorkspaceMemberResponse;
import com.capston.demo.domain.user.dto.workspace.WorkspaceResponse;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.entity.Workspace;
import com.capston.demo.domain.user.entity.WorkspaceInvitation;
import com.capston.demo.domain.user.entity.WorkspaceMember;
import com.capston.demo.domain.user.repository.UserRepository;
import com.capston.demo.domain.user.repository.WorkspaceInvitationRepository;
import com.capston.demo.domain.user.repository.WorkspaceMemberRepository;
import com.capston.demo.domain.user.repository.WorkspaceRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingTranscriptMongoRepository transcriptRepository;
    private final TaskRepository taskRepository;
    private final EventRepository eventRepository;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Transactional
    public WorkspaceResponse createWorkspace(WorkspaceCreateRequest request, Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String slug = generateUniqueSlug(request.getName());
        Workspace workspace = new Workspace(
                request.getName(),
                slug,
                owner,
                normalizeText(request.getMeetingCategory()),
                normalizeText(request.getMeetingContext())
        );
        workspaceRepository.save(workspace);

        WorkspaceMember ownerMember = new WorkspaceMember(workspace, owner, WorkspaceMember.MemberRole.owner);
        workspaceMemberRepository.save(ownerMember);

        return new WorkspaceResponse(workspace);
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(Long workspaceId, WorkspaceUpdateRequest request, Long userId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        if (!workspace.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKSPACE_OWNER_REQUIRED);
        }
        workspace.update(
                normalizeText(request.getName()),
                normalizeText(request.getMeetingCategory()),
                normalizeText(request.getMeetingContext())
        );
        return new WorkspaceResponse(workspace);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getMyWorkspaces(Long userId) {
        return workspaceRepository.findAllByMemberId(userId)
                .stream().map(WorkspaceResponse::new).collect(Collectors.toList());
    }

    @Transactional
    public void inviteMember(Long workspaceId, WorkspaceInviteRequest request, Long inviterId) {
        if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, inviterId)) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        User invitee = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, invitee.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_MEMBER);
        }
        if (invitationRepository.existsByWorkspace_IdAndInvitee_IdAndStatus(
                workspaceId, invitee.getId(), WorkspaceInvitation.InvitationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.ALREADY_INVITED);
        }

        User inviter = userRepository.findById(inviterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        invitationRepository.save(new WorkspaceInvitation(workspace, invitee, inviter));
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> getPendingInvitations(Long userId) {
        return invitationRepository
                .findByInvitee_IdAndStatus(userId, WorkspaceInvitation.InvitationStatus.PENDING)
                .stream().map(InvitationResponse::new).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InvitationCountResponse getPendingInvitationCount(Long userId) {
        long count = invitationRepository.countByInvitee_IdAndStatus(
                userId, WorkspaceInvitation.InvitationStatus.PENDING);
        return new InvitationCountResponse(count);
    }

    @Transactional
    public void acceptInvitation(Long invitationId, Long userId) {
        WorkspaceInvitation invitation = invitationRepository.findByIdAndInvitee_Id(invitationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));

        if (invitation.getStatus() != WorkspaceInvitation.InvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVITATION_ALREADY_PROCESSED);
        }

        invitation.accept();

        Workspace workspace = invitation.getWorkspace();
        User invitee = invitation.getInvitee();
        if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspace.getId(), invitee.getId())) {
            workspaceMemberRepository.save(new WorkspaceMember(workspace, invitee));
        }
    }

    @Transactional
    public void declineInvitation(Long invitationId, Long userId) {
        WorkspaceInvitation invitation = invitationRepository.findByIdAndInvitee_Id(invitationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));

        if (invitation.getStatus() != WorkspaceInvitation.InvitationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVITATION_ALREADY_PROCESSED);
        }

        invitation.decline();
    }

    @Transactional
    public void leaveWorkspace(Long workspaceId, Long userId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        if (workspace.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.OWNER_CANNOT_LEAVE);
        }
        if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, userId)) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }

        workspaceMemberRepository.deleteByWorkspace_IdAndUser_Id(workspaceId, userId);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> getMembers(Long workspaceId, Long userId) {
        if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, userId)) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        return workspaceMemberRepository.findByWorkspace_Id(workspaceId)
                .stream().map(WorkspaceMemberResponse::new).collect(Collectors.toList());
    }

    @Transactional
    public void deleteWorkspace(Long workspaceId, Long userId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        if (!workspace.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.WORKSPACE_OWNER_REQUIRED);
        }

        List<Meeting> meetings = meetingRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        List<Long> meetingIds = meetings.stream().map(Meeting::getId).collect(Collectors.toList());

        // S3 오디오 파일 삭제
        for (Meeting meeting : meetings) {
            for (MeetingRecording recording : meeting.getRecordings()) {
                try {
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(recording.getS3Bucket())
                            .key(recording.getS3Key())
                            .build());
                } catch (Exception e) {
                    log.warn("S3 파일 삭제 실패. key={}", recording.getS3Key(), e);
                }
            }
        }

        // MongoDB 트랜스크립트 삭제
        if (!meetingIds.isEmpty()) {
            transcriptRepository.deleteByMeetingIdIn(meetingIds);
        }

        // tasks, events 삭제 (event_participants는 CascadeType.ALL로 자동 삭제)
        taskRepository.deleteByWorkspaceId(workspaceId);
        eventRepository.deleteByWorkspaceId(workspaceId);

        // meetings 삭제 (meeting_recordings는 CascadeType.ALL로 자동 삭제)
        meetingRepository.deleteAll(meetings);

        invitationRepository.deleteByWorkspace_Id(workspaceId);
        workspaceMemberRepository.deleteByWorkspace_Id(workspaceId);
        workspaceRepository.delete(workspace);
    }

    private String generateUniqueSlug(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }
}
