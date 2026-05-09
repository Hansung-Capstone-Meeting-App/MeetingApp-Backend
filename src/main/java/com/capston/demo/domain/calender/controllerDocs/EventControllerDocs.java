package com.capston.demo.domain.calender.controllerDocs;

import com.capston.demo.domain.calender.dto.request.EventCreateRequest;
import com.capston.demo.domain.calender.dto.request.EventUpdateRequest;
import com.capston.demo.domain.calender.dto.response.EventResponse;
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

@Tag(name = "Event", description = "일정 조회/생성/수정/삭제 API")
public interface EventControllerDocs {

    @Operation(
            summary = "일정 목록 조회 (팀 전체, relatedTasks 포함)",
            description = "meetingId 또는 workspaceId 기준으로 팀 전체 일정을 반환합니다.\n\n" +
                    "각 일정에 같은 meetingId를 가진 할일(`relatedTasks`) 목록과 status가 포함됩니다.",
            parameters = {
                    @Parameter(name = "meetingId", description = "회의 ID", example = "1"),
                    @Parameter(name = "workspaceId", description = "워크스페이스 ID", example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "meetingId 또는 workspaceId 누락")
            }
    )
    ResponseEntity<List<EventResponse>> getEvents(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                  Long meetingId, Long workspaceId);

    @Operation(
            summary = "일정 단건 조회 (relatedTasks 포함)",
            parameters = {
                    @Parameter(name = "id", description = "일정 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
                    @ApiResponse(responseCode = "404", description = "일정을 찾을 수 없음")
            }
    )
    ResponseEntity<EventResponse> getEvent(@AuthenticationPrincipal CustomUserDetails userDetails, Long id);

    @Operation(
            summary = "일정 수동 생성",
            description = "사용자가 직접 일정을 등록합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = EventCreateRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "title": "배포 완료 목표일",
                                      "description": "1차 배포 마감",
                                      "startAt": "2026-05-25T00:00:00",
                                      "endAt": "2026-05-25T23:59:59",
                                      "isAllDay": true,
                                      "workspaceId": 1,
                                      "meetingId": 3,
                                      "color": "red",
                                      "participantUserIds": [2, 3]
                                    }
                                    """)
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "생성 성공"),
                    @ApiResponse(responseCode = "400", description = "필수 필드 누락"),
                    @ApiResponse(responseCode = "403", description = "워크스페이스 멤버 아님")
            }
    )
    ResponseEntity<EventResponse> createEvent(@AuthenticationPrincipal CustomUserDetails userDetails,
                                              EventCreateRequest request);

    @Operation(
            summary = "일정 수정 (부분 업데이트)",
            description = "제공된 필드만 업데이트합니다. null 필드는 기존 값 유지.",
            parameters = {
                    @Parameter(name = "id", description = "일정 ID", example = "1", required = true)
            },
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = EventUpdateRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "title": "배포 완료 목표일 (수정)",
                                      "startAt": "2026-05-26T00:00:00",
                                      "endAt": "2026-05-26T23:59:59"
                                    }
                                    """)
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "수정 성공"),
                    @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
                    @ApiResponse(responseCode = "404", description = "일정을 찾을 수 없음")
            }
    )
    ResponseEntity<EventResponse> updateEvent(@AuthenticationPrincipal CustomUserDetails userDetails,
                                              Long id, EventUpdateRequest request);

    @Operation(
            summary = "일정 삭제",
            parameters = {
                    @Parameter(name = "id", description = "일정 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "삭제 성공"),
                    @ApiResponse(responseCode = "403", description = "접근 권한 없음"),
                    @ApiResponse(responseCode = "404", description = "일정을 찾을 수 없음")
            }
    )
    ResponseEntity<Void> deleteEvent(@AuthenticationPrincipal CustomUserDetails userDetails, Long id);
}
