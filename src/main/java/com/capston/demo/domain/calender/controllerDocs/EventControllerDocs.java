package com.capston.demo.domain.calender.controllerDocs;

import com.capston.demo.domain.calender.dto.response.EventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Event", description = "일정 조회 API")
public interface EventControllerDocs {

    @Operation(
            summary = "일정 목록 조회",
            description = "meetingId 또는 workspaceId 기준으로 저장된 일정을 조회합니다. meetingId가 있으면 회의 기준으로 우선 조회합니다.",
            parameters = {
                    @Parameter(name = "meetingId", description = "회의 ID", example = "11"),
                    @Parameter(name = "workspaceId", description = "워크스페이스 ID", example = "1")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "meetingId 또는 workspaceId 누락")
            }
    )
    ResponseEntity<List<EventResponse>> getEvents(Long meetingId, Long workspaceId);
}
