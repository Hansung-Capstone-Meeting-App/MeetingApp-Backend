package com.capston.demo.domain.meeting.controllerDocs;

import com.capston.demo.domain.meeting.dto.request.MeetingRequest;
import com.capston.demo.domain.meeting.dto.request.SpeakerMappingRequest;
import com.capston.demo.domain.meeting.dto.request.TranscriptRequest;
import com.capston.demo.domain.meeting.dto.response.MeetingResponse;
import com.capston.demo.domain.meeting.dto.response.SpeakerMappingResponse;
import com.capston.demo.domain.meeting.dto.response.TranscriptResponse;
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

@Tag(name = "Meeting", description = "회의 생성/조회 및 트랜스크립트·화자 매핑 API")
public interface MeetingControllerDocs {

    // ── 회의 ──────────────────────────────────────────────────────────────────

    @Operation(
            summary = "회의 시작",
            description = "새 회의를 생성하고 시작 시각을 기록합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MeetingRequest.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "workspaceId": 1,
                                              "channelId": 2,
                                              "title": "주간 팀 회의"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "회의 생성 성공"),
                    @ApiResponse(responseCode = "400", description = "잘못된 요청")
            }
    )
    ResponseEntity<MeetingResponse> startMeeting(@AuthenticationPrincipal CustomUserDetails userDetails, MeetingRequest request);

    @Operation(
            summary = "회의 종료",
            description = "회의 종료 시각(endedAt)을 기록합니다.",
            parameters = {
                    @Parameter(name = "id", description = "회의 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "회의 종료 성공"),
                    @ApiResponse(responseCode = "404", description = "회의를 찾을 수 없음")
            }
    )
    ResponseEntity<MeetingResponse> endMeeting(@AuthenticationPrincipal CustomUserDetails userDetails, Long id);

    @Operation(
            summary = "회의 단건 조회",
            parameters = {
                    @Parameter(name = "id", description = "회의 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "404", description = "회의를 찾을 수 없음")
            }
    )
    ResponseEntity<MeetingResponse> getMeeting(@AuthenticationPrincipal CustomUserDetails userDetails, Long id);

    @Operation(
            summary = "회의 목록 조회",
            description = "workspaceId, channelId 중 하나 이상으로 필터링할 수 있습니다. 둘 다 생략하면 전체 목록을 반환합니다.",
            parameters = {
                    @Parameter(name = "workspaceId", description = "워크스페이스 ID (선택)", example = "1"),
                    @Parameter(name = "channelId", description = "채널 ID (선택)", example = "2")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<List<MeetingResponse>> getMeetings(@AuthenticationPrincipal CustomUserDetails userDetails, Long workspaceId, Long channelId);

    @Operation(
            summary = "회의 삭제",
            parameters = {
                    @Parameter(name = "id", description = "회의 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "삭제 성공"),
                    @ApiResponse(responseCode = "404", description = "회의를 찾을 수 없음")
            }
    )
    ResponseEntity<Void> deleteMeeting(@AuthenticationPrincipal CustomUserDetails userDetails, Long id);

    // ── 트랜스크립트 ───────────────────────────────────────────────────────────

    @Operation(
            summary = "트랜스크립트 저장",
            description = "STT 서버가 음성 인식 결과를 저장할 때 호출합니다.\n\n" +
                    "전체 텍스트, 요약, 키워드, 화자별 세그먼트를 함께 저장합니다.",
            parameters = {
                    @Parameter(name = "meetingId", description = "회의 ID", example = "1", required = true)
            },
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TranscriptRequest.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "recordingId": 1,
                                              "fullText": "안녕하세요. 오늘 회의를 시작하겠습니다.",
                                              "summary": "팀 주간 업무 공유 및 다음 스프린트 계획 논의",
                                              "keywords": "[\\"스프린트\\", \\"배포\\", \\"마감\\"]",
                                              "segments": [
                                                {
                                                  "speakerLabel": "SPEAKER_01",
                                                  "userId": null,
                                                  "content": "안녕하세요. 오늘 회의를 시작하겠습니다.",
                                                  "startSec": 0.0,
                                                  "endSec": 3.5,
                                                  "sequence": 1
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "저장 성공"),
                    @ApiResponse(responseCode = "404", description = "회의를 찾을 수 없음")
            }
    )
    ResponseEntity<TranscriptResponse> saveTranscript(Long meetingId, TranscriptRequest request);

    @Operation(
            summary = "트랜스크립트 조회",
            description = "회의의 STT 결과(전체 텍스트, 요약, 세그먼트 목록)를 반환합니다.",
            parameters = {
                    @Parameter(name = "meetingId", description = "회의 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "404", description = "트랜스크립트를 찾을 수 없음")
            }
    )
    ResponseEntity<TranscriptResponse> getTranscript(@AuthenticationPrincipal CustomUserDetails userDetails, Long meetingId);

    // ── 화자 매핑 ─────────────────────────────────────────────────────────────

    @Operation(
            summary = "화자 매핑 저장",
            description = "STT가 부여한 화자 레이블을 이름과 연결합니다.\n\n" +
                    "- `userName`: 화자 이름 직접 입력 (필수)\n" +
                    "- `userId`: 추후 메신저 기능 연동 시 사용 (현재는 생략 가능)\n" +
                    "- 기존 매핑이 있으면 덮어씁니다.",
            parameters = {
                    @Parameter(name = "transcriptId", description = "트랜스크립트 ID", example = "1", required = true)
            },
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SpeakerMappingRequest.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "mappings": [
                                                { "speakerLabel": "A", "userName": "김철수" },
                                                { "speakerLabel": "B", "userName": "박영희" }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "매핑 저장 성공"),
                    @ApiResponse(responseCode = "404", description = "트랜스크립트를 찾을 수 없음")
            }
    )
    ResponseEntity<List<SpeakerMappingResponse>> saveSpeakerMappings(String transcriptId, SpeakerMappingRequest request);

    @Operation(
            summary = "화자 매핑 목록 조회",
            parameters = {
                    @Parameter(name = "transcriptId", description = "트랜스크립트 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "404", description = "트랜스크립트를 찾을 수 없음")
            }
    )
    ResponseEntity<List<SpeakerMappingResponse>> getSpeakerMappings(String transcriptId);
}
