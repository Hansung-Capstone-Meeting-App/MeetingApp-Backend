package com.capston.demo.domain.user.controllerDocs;

import com.capston.demo.domain.user.dto.workspace.WorkspaceCreateRequest;
import com.capston.demo.domain.user.dto.workspace.WorkspaceInviteRequest;
import com.capston.demo.domain.user.dto.workspace.WorkspaceMemberResponse;
import com.capston.demo.domain.user.dto.workspace.WorkspaceResponse;
import com.capston.demo.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@Tag(name = "Workspace", description = "워크스페이스 생성/조회 및 멤버 관리 API")
public interface WorkspaceControllerDocs {

    @Operation(
            summary = "워크스페이스 생성",
            description = "새 워크스페이스를 생성합니다. 생성자가 자동으로 owner 멤버로 등록됩니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WorkspaceCreateRequest.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "name": "한성대 캡스톤팀"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "생성 성공"),
                    @ApiResponse(responseCode = "400", description = "name이 비어 있음")
            }
    )
    ResponseEntity<WorkspaceResponse> createWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            WorkspaceCreateRequest request);

    @Operation(
            summary = "내 워크스페이스 목록 조회",
            description = "내가 owner이거나 멤버로 소속된 모든 워크스페이스를 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<List<WorkspaceResponse>> getMyWorkspaces(
            @AuthenticationPrincipal CustomUserDetails userDetails);

    @Operation(
            summary = "워크스페이스 멤버 초대",
            description = "이메일로 기존 회원을 워크스페이스에 초대합니다. 요청자가 해당 워크스페이스의 멤버여야 합니다.",
            parameters = {
                    @Parameter(name = "workspaceId", description = "워크스페이스 ID", example = "1", required = true)
            },
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WorkspaceInviteRequest.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "email": "member@example.com"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "204", description = "초대 성공"),
                    @ApiResponse(responseCode = "400", description = "유효하지 않은 이메일 형식"),
                    @ApiResponse(responseCode = "403", description = "요청자가 워크스페이스 멤버가 아님"),
                    @ApiResponse(responseCode = "404", description = "해당 이메일의 사용자를 찾을 수 없음")
            }
    )
    ResponseEntity<Void> inviteMember(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Long workspaceId,
            WorkspaceInviteRequest request);

    @Operation(
            summary = "워크스페이스 멤버 목록 조회",
            description = "워크스페이스에 소속된 전체 멤버를 반환합니다. 화자 매핑 드롭다운 등에 활용합니다.",
            parameters = {
                    @Parameter(name = "workspaceId", description = "워크스페이스 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "403", description = "요청자가 워크스페이스 멤버가 아님")
            }
    )
    ResponseEntity<List<WorkspaceMemberResponse>> getMembers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Long workspaceId);

    @Operation(
            summary = "워크스페이스 삭제",
            description = "워크스페이스와 전체 멤버십을 삭제합니다. **소유자(owner)만** 호출 가능합니다.",
            parameters = {
                    @Parameter(name = "workspaceId", description = "워크스페이스 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "삭제 성공"),
                    @ApiResponse(responseCode = "403", description = "소유자가 아님"),
                    @ApiResponse(responseCode = "404", description = "워크스페이스를 찾을 수 없음")
            }
    )
    ResponseEntity<Void> deleteWorkspace(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Long workspaceId);
}
