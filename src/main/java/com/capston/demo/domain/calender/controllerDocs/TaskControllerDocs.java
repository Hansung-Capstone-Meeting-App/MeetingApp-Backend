package com.capston.demo.domain.calender.controllerDocs;

import com.capston.demo.domain.calender.dto.request.TaskCreateRequest;
import com.capston.demo.domain.calender.dto.request.TaskUpdateRequest;
import com.capston.demo.domain.calender.dto.response.TaskResponse;
import com.capston.demo.domain.calender.dto.response.TaskStatsResponse;
import com.capston.demo.domain.calender.entity.TaskStatus;
import com.capston.demo.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Task", description = "할일 조회/생성/수정/삭제 API")
public interface TaskControllerDocs {

    @Operation(
            summary = "할일 목록 조회 (팀 전체, 상태·마감일 필터 가능)",
            description = "meetingId, workspaceId, assigneeId 중 하나를 기준으로 팀 전체 할일을 조회합니다.\n\n" +
                    "- `status`: `TODO` | `IN_PROGRESS` | `DONE` (생략 시 전체)\n" +
                    "- `dueBefore`: 해당 일시 이전 마감 건만 반환 (형식: `yyyy-MM-dd'T'HH:mm:ss`)",
            parameters = {
                    @Parameter(name = "meetingId", description = "회의 ID", example = "1"),
                    @Parameter(name = "workspaceId", description = "워크스페이스 ID", example = "1"),
                    @Parameter(name = "assigneeId", description = "담당자 userId", example = "2"),
                    @Parameter(name = "status", description = "상태 필터", example = "TODO"),
                    @Parameter(name = "dueBefore", description = "마감일 이전 필터", example = "2026-05-31T23:59:59")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "조회 기준 누락")
            }
    )
    ResponseEntity<List<TaskResponse>> getTasks(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                Long meetingId, Long workspaceId, Long assigneeId,
                                                TaskStatus status, java.time.LocalDateTime dueBefore);

    @Operation(
            summary = "할일 상태 분포 조회 (칸반 보드용)",
            description = "workspaceId 또는 meetingId 기준으로 TODO/IN_PROGRESS/DONE 각 개수와 합계를 반환합니다.",
            parameters = {
                    @Parameter(name = "workspaceId", description = "워크스페이스 ID", example = "1"),
                    @Parameter(name = "meetingId", description = "회의 ID", example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "workspaceId 또는 meetingId 누락")
            }
    )
    ResponseEntity<TaskStatsResponse> getTaskStats(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                    Long workspaceId, Long meetingId);

    @Operation(
            summary = "할일 단건 조회",
            parameters = {
                    @Parameter(name = "id", description = "할일 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
                    @ApiResponse(responseCode = "404", description = "할일을 찾을 수 없음")
            }
    )
    ResponseEntity<TaskResponse> getTask(@AuthenticationPrincipal CustomUserDetails userDetails, Long id);

    @Operation(
            summary = "할일 수동 생성",
            description = "사용자가 직접 할일을 등록합니다. AI 추출 외 수동 추가용입니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TaskCreateRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "title": "API 명세서 작성",
                                      "description": "Swagger 기준으로 정리",
                                      "assigneeName": "김철수",
                                      "assigneeId": 2,
                                      "dueDate": "2026-05-20T18:00:00",
                                      "workspaceId": 1,
                                      "meetingId": 3
                                    }
                                    """)
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "생성 성공"),
                    @ApiResponse(responseCode = "400", description = "title 누락"),
                    @ApiResponse(responseCode = "403", description = "워크스페이스 멤버 아님")
            }
    )
    ResponseEntity<TaskResponse> createTask(@AuthenticationPrincipal CustomUserDetails userDetails,
                                            TaskCreateRequest request);

    @Operation(
            summary = "할일 수정 (부분 업데이트)",
            description = "제공된 필드만 업데이트합니다. null 필드는 기존 값 유지.\n\n" +
                    "**status 값**: `TODO` | `IN_PROGRESS` | `DONE`",
            parameters = {
                    @Parameter(name = "id", description = "할일 ID", example = "1", required = true)
            },
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TaskUpdateRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "status": "IN_PROGRESS"
                                    }
                                    """)
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "수정 성공"),
                    @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
                    @ApiResponse(responseCode = "404", description = "할일을 찾을 수 없음")
            }
    )
    ResponseEntity<TaskResponse> updateTask(@AuthenticationPrincipal CustomUserDetails userDetails,
                                            Long id, TaskUpdateRequest request);

    @Operation(
            summary = "할일 삭제",
            parameters = {
                    @Parameter(name = "id", description = "할일 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "삭제 성공"),
                    @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
                    @ApiResponse(responseCode = "404", description = "할일을 찾을 수 없음")
            }
    )
    ResponseEntity<Void> deleteTask(@AuthenticationPrincipal CustomUserDetails userDetails, Long id);
}
