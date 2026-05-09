package com.capston.demo.domain.calender.controllerDocs;

import com.capston.demo.domain.calender.dto.request.NotionSyncRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Calendar", description = "Notion 캘린더 동기화 및 이벤트 조회 API")
public interface CalendarControllerDocs {

    @Operation(
            summary = "단일 이벤트 Notion 동기화",
            description = "특정 이벤트를 로그인한 사용자의 Notion 캘린더 데이터베이스에 동기화합니다.\n\n" +
                    "**사전 조건**\n" +
                    "- Notion 계정 연동 (`POST /api/oauth2/notion`)\n" +
                    "- 캘린더 DB 등록 (`PUT /api/oauth2/notion/calendar-database`)\n" +
                    "- 요청자가 해당 이벤트의 워크스페이스 멤버",
            parameters = {
                    @Parameter(name = "eventId", description = "이벤트 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "동기화 성공 — eventId, notionPageId 반환"),
                    @ApiResponse(responseCode = "400", description = "Notion 캘린더 DB가 미등록"),
                    @ApiResponse(responseCode = "403", description = "Notion 미연동 또는 워크스페이스 멤버 아님"),
                    @ApiResponse(responseCode = "404", description = "이벤트를 찾을 수 없음")
            }
    )
    ResponseEntity<?> syncEventToNotion(Long eventId);

    @Operation(
            summary = "워크스페이스 전체 이벤트 Notion 일괄 동기화",
            description = "워크스페이스에 속한 로그인 사용자가 생성한 모든 이벤트를 Notion에 동기화합니다.",
            parameters = {
                    @Parameter(name = "workspaceId", description = "워크스페이스 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "동기화 성공 — workspaceId, syncedCount, results 반환"),
                    @ApiResponse(responseCode = "400", description = "Notion 캘린더 DB 미등록"),
                    @ApiResponse(responseCode = "403", description = "Notion 미연동 또는 워크스페이스 멤버 아님")
            }
    )
    ResponseEntity<?> syncWorkspaceEventsToNotion(Long workspaceId);

    @Operation(
            summary = "워크스페이스 이벤트 목록 조회",
            description = "워크스페이스에 속한 로그인 사용자가 생성한 이벤트 목록을 반환합니다.",
            parameters = {
                    @Parameter(name = "workspaceId", description = "워크스페이스 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "403", description = "워크스페이스 멤버 아님")
            }
    )
    ResponseEntity<?> getWorkspaceEvents(Long workspaceId);

    @Operation(
            summary = "이벤트 ID 목록 일괄 Notion 동기화",
            description = "요청 본문에 지정한 이벤트 ID 목록을 Notion에 일괄 동기화합니다.\n\n" +
                    "각 이벤트마다 워크스페이스 멤버십을 검증하며, 권한 없는 이벤트는 `FORBIDDEN_WORKSPACE`로 표시됩니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NotionSyncRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "eventIds": [1, 2, 3]
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "처리 완료 — requestedCount, results(SUCCESS/NOT_FOUND/FORBIDDEN_WORKSPACE) 반환"),
                    @ApiResponse(responseCode = "400", description = "eventIds 누락 또는 Notion 캘린더 DB 미등록"),
                    @ApiResponse(responseCode = "403", description = "Notion 미연동")
            }
    )
    ResponseEntity<?> syncEventsToNotionBatch(NotionSyncRequestDto request);
}
