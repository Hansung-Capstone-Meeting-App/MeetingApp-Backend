package com.capston.demo.domain.user.controllerDocs;

import com.capston.demo.domain.user.dto.workspace.InvitationResponse;
import com.capston.demo.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@Tag(name = "Invitation", description = "워크스페이스 초대 수락/거절 API")
public interface InvitationControllerDocs {

    @Operation(
            summary = "받은 초대 목록 조회",
            description = "내게 온 PENDING 상태의 초대 목록을 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<List<InvitationResponse>> getMyInvitations(
            @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(
            summary = "초대 수락",
            description = "초대를 수락하면 해당 워크스페이스의 멤버로 자동 등록됩니다.",
            parameters = {
                    @Parameter(name = "invitationId", description = "초대 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "수락 성공"),
                    @ApiResponse(responseCode = "404", description = "초대를 찾을 수 없음"),
                    @ApiResponse(responseCode = "409", description = "이미 처리된 초대")
            }
    )
    ResponseEntity<Void> acceptInvitation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Long invitationId);

    @Operation(
            summary = "초대 거절",
            description = "초대를 거절하면 워크스페이스 멤버로 등록되지 않습니다.",
            parameters = {
                    @Parameter(name = "invitationId", description = "초대 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "거절 성공"),
                    @ApiResponse(responseCode = "404", description = "초대를 찾을 수 없음"),
                    @ApiResponse(responseCode = "409", description = "이미 처리된 초대")
            }
    )
    ResponseEntity<Void> declineInvitation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Long invitationId);
}
