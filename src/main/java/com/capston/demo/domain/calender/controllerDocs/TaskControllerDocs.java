package com.capston.demo.domain.calender.controllerDocs;

import com.capston.demo.domain.calender.dto.response.TaskResponse;
import com.capston.demo.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Task", description = "할 일 조회 API")
public interface TaskControllerDocs {

    @Operation(
            summary = "할 일 목록 조회",
            description = "meetingId, workspaceId, assigneeId 중 하나를 기준으로 로그인한 사용자가 생성한 할 일을 조회합니다. meetingId를 가장 우선으로 사용합니다.",
            parameters = {
                    @Parameter(name = "meetingId", description = "회의 ID", example = "11"),
                    @Parameter(name = "workspaceId", description = "워크스페이스 ID", example = "1"),
                    @Parameter(name = "assigneeId", description = "담당자 userId", example = "12")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "조회 기준 누락")
            }
    )
    ResponseEntity<List<TaskResponse>> getTasks(@AuthenticationPrincipal CustomUserDetails userDetails, Long meetingId, Long workspaceId, Long assigneeId);
}
