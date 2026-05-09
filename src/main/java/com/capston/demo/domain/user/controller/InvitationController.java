package com.capston.demo.domain.user.controller;

import com.capston.demo.domain.user.controllerDocs.InvitationControllerDocs;
import com.capston.demo.domain.user.dto.workspace.InvitationResponse;
import com.capston.demo.domain.user.service.WorkspaceService;
import com.capston.demo.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController implements InvitationControllerDocs {

    private final WorkspaceService workspaceService;

    // GET /api/invitations
    @GetMapping
    public ResponseEntity<List<InvitationResponse>> getMyInvitations(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(workspaceService.getPendingInvitations(userDetails.getUserId()));
    }

    // POST /api/invitations/{invitationId}/accept
    @PostMapping("/{invitationId}/accept")
    public ResponseEntity<Void> acceptInvitation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long invitationId) {
        workspaceService.acceptInvitation(invitationId, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    // POST /api/invitations/{invitationId}/decline
    @PostMapping("/{invitationId}/decline")
    public ResponseEntity<Void> declineInvitation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long invitationId) {
        workspaceService.declineInvitation(invitationId, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }
}
