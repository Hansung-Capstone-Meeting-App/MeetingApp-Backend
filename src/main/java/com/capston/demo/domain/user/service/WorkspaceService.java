package com.capston.demo.domain.user.service;

import com.capston.demo.domain.user.dto.workspace.WorkspaceCreateRequest;
import com.capston.demo.domain.user.dto.workspace.WorkspaceInviteRequest;
import com.capston.demo.domain.user.dto.workspace.WorkspaceMemberResponse;
import com.capston.demo.domain.user.dto.workspace.WorkspaceResponse;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.entity.Workspace;
import com.capston.demo.domain.user.entity.WorkspaceMember;
import com.capston.demo.domain.user.repository.UserRepository;
import com.capston.demo.domain.user.repository.WorkspaceMemberRepository;
import com.capston.demo.domain.user.repository.WorkspaceRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public WorkspaceResponse createWorkspace(WorkspaceCreateRequest request, Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String slug = generateUniqueSlug(request.getName());
        Workspace workspace = new Workspace(request.getName(), slug, owner);
        workspaceRepository.save(workspace);

        // 생성자를 owner 역할로 멤버에 자동 추가
        WorkspaceMember ownerMember = new WorkspaceMember(workspace, owner, WorkspaceMember.MemberRole.owner);
        workspaceMemberRepository.save(ownerMember);

        return new WorkspaceResponse(workspace);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getMyWorkspaces(Long userId) {
        return workspaceRepository.findAllByMemberId(userId)
                .stream().map(WorkspaceResponse::new).collect(Collectors.toList());
    }

    @Transactional
    public void inviteMember(Long workspaceId, WorkspaceInviteRequest request, Long userId) {
        if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, userId)) {
            throw new BusinessException(ErrorCode.NOT_WORKSPACE_MEMBER);
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        User invitee = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspaceId, invitee.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_MEMBER);
        }

        workspaceMemberRepository.save(new WorkspaceMember(workspace, invitee));
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
        workspaceMemberRepository.deleteByWorkspace_Id(workspaceId);
        workspaceRepository.delete(workspace);
    }

    private String generateUniqueSlug(String name) {
        String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        String slug = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        // slug unique 충돌은 UUID suffix가 사실상 막아주므로 별도 루프 불필요
        return slug;
    }
}
