package com.capston.demo.domain.user.controller;

import com.capston.demo.domain.user.controllerDocs.WorkspaceControllerDocs;
import com.capston.demo.domain.user.dto.workspace.WorkspaceCreateRequest;
import com.capston.demo.domain.user.dto.workspace.WorkspaceInviteRequest;
import com.capston.demo.domain.user.dto.workspace.WorkspaceMemberResponse;
import com.capston.demo.domain.user.dto.workspace.WorkspaceResponse;
import com.capston.demo.domain.user.service.WorkspaceService;
import com.capston.demo.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController implements WorkspaceControllerDocs {

    private final WorkspaceService workspaceService;

    // 워크스페이스 생성
    // POST /api/workspaces
    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody WorkspaceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workspaceService.createWorkspace(request, userDetails.getUserId()));
    }

    // 내가 속한 워크스페이스 목록 조회 (owner + member 모두 포함)
    // GET /api/workspaces
    @GetMapping
    public ResponseEntity<List<WorkspaceResponse>> getMyWorkspaces(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(workspaceService.getMyWorkspaces(userDetails.getUserId()));
    }

    // 워크스페이스 멤버 초대
    // POST /api/workspaces/{workspaceId}/members
    @PostMapping("/{workspaceId}/members")
    public ResponseEntity<Void> inviteMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId,
            @Valid @RequestBody WorkspaceInviteRequest request) {
        workspaceService.inviteMember(workspaceId, request, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    // 워크스페이스 멤버 목록 조회 (화자 매핑용)
    // GET /api/workspaces/{workspaceId}/members
    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<List<WorkspaceMemberResponse>> getMembers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        return ResponseEntity.ok(workspaceService.getMembers(workspaceId, userDetails.getUserId()));
    }

    // 워크스페이스 나가기 (owner 제외)
    // DELETE /api/workspaces/{workspaceId}/members/me
    @DeleteMapping("/{workspaceId}/members/me")
    public ResponseEntity<Void> leaveWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        workspaceService.leaveWorkspace(workspaceId, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    // 워크스페이스 삭제 (owner만 가능)
    // DELETE /api/workspaces/{workspaceId}
    @DeleteMapping("/{workspaceId}")
    public ResponseEntity<Void> deleteWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long workspaceId) {
        workspaceService.deleteWorkspace(workspaceId, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }
}
