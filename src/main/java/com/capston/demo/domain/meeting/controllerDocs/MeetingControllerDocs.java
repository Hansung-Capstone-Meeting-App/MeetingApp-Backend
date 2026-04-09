package com.capston.demo.domain.meeting.controllerDocs;

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

@Tag(name = "Meeting", description = "회의 조회 및 트랜스크립트·화자 매핑 API (회의 생성은 Slack 파일 업로드로 자동 처리)")
public interface MeetingControllerDocs {

    // ── 회의 ──────────────────────────────────────────────────────────────────

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
            summary = "내 회의 목록 조회",
            description = "내가 생성한 회의 목록을 최신순으로 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<List<MeetingResponse>> getMeetings(@AuthenticationPrincipal CustomUserDetails userDetails);

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
            description = "STT가 부여한 화자 레이블을 Slack 사용자와 연결합니다.\n\n" +
                    "- `slackUserId`: Slack 사용자 ID (U로 시작)\n" +
                    "- `userName`: 표시 이름\n" +
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
                                                { "speakerLabel": "A", "userName": "김철수", "slackUserId": "U12345" },
                                                { "speakerLabel": "B", "userName": "박영희", "slackUserId": "U67890" }
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
