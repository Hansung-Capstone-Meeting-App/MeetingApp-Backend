package com.capston.demo.domain.recording.controllerDocs;

import com.capston.demo.domain.meeting.entity.RecordingStatus;
import com.capston.demo.domain.recording.dto.request.PresignedUploadRequest;
import com.capston.demo.domain.recording.dto.response.PresignedUrlResponse;
import com.capston.demo.domain.recording.dto.response.RecordingResponse;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "Recording", description = "음성 녹음 파일 업로드 및 조회 API")
public interface RecordingControllerDocs {

    @Operation(
            summary = "음성 파일 서버 경유 업로드",
            description = "서버를 통해 음성 파일을 S3에 업로드합니다.\n\n" +
                    "- Content-Type: `multipart/form-data`\n" +
                    "- 허용 확장자: `.m4a`, `.mp3`\n" +
                    "- 업로드 완료 후 DB에 녹음 레코드가 자동 생성됩니다.",
            parameters = {
                    @Parameter(name = "meetingId", description = "회의 ID", example = "1", required = true),
                    @Parameter(name = "file", description = "업로드할 음성 파일 (.m4a 또는 .mp3)", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "업로드 성공"),
                    @ApiResponse(responseCode = "400", description = "허용되지 않는 파일 형식 또는 빈 파일"),
                    @ApiResponse(responseCode = "404", description = "회의를 찾을 수 없음")
            }
    )
    ResponseEntity<RecordingResponse> upload(Long meetingId, MultipartFile file) throws IOException;

    @Operation(
            summary = "회의별 녹음 목록 조회",
            description = "특정 회의에 업로드된 모든 녹음 파일 목록을 반환합니다.",
            parameters = {
                    @Parameter(name = "meetingId", description = "회의 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<List<RecordingResponse>> getRecordingsByMeeting(Long meetingId);

    @Operation(
            summary = "녹음 처리 상태 변경",
            description = "녹음 파일의 처리 상태를 변경합니다. 주로 STT 서버에서 처리 완료 후 호출합니다.\n\n" +
                    "상태값: `UPLOADED` → `PROCESSING` → `DONE` / `FAILED`",
            parameters = {
                    @Parameter(name = "recordingId", description = "녹음 ID", example = "1", required = true),
                    @Parameter(name = "status", description = "변경할 상태값", example = "DONE", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "상태 변경 성공"),
                    @ApiResponse(responseCode = "404", description = "녹음 파일을 찾을 수 없음")
            }
    )
    ResponseEntity<RecordingResponse> updateStatus(Long recordingId, RecordingStatus status);

    @Operation(
            summary = "S3 직접 업로드용 Presigned PUT URL 발급(거의 사용 안함)",
            description = "클라이언트가 서버를 거치지 않고 S3에 직접 파일을 업로드할 수 있는 서명된 URL을 발급합니다.\n\n" +
                    "**사용 순서**\n" +
                    "1. 이 API로 `presignedUrl`과 `s3Key`를 받습니다.\n" +
                    "2. `presignedUrl`로 `PUT` 요청을 보내 파일을 S3에 직접 업로드합니다.\n" +
                    "3. 업로드 완료 후 `s3Key`를 사용해 DB에 녹음 레코드를 등록합니다.\n\n" +
                    "URL 유효시간: 1시간",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PresignedUploadRequest.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "meetingId": 1,
                                              "filename": "회의녹음.m4a"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "URL 발급 성공",
                            content = @Content(
                                    schema = @Schema(implementation = PresignedUrlResponse.class),
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                      "presignedUrl": "https://s3.amazonaws.com/bucket/recordings/1/uuid.m4a?X-Amz-Signature=...",
                                                      "s3Key": "recordings/1/uuid.m4a",
                                                      "expiresAt": "2024-01-01T13:00:00"
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "확장자 없는 파일명 또는 잘못된 요청"),
                    @ApiResponse(responseCode = "404", description = "회의를 찾을 수 없음")
            }
    )
    ResponseEntity<PresignedUrlResponse> getUploadPresignedUrl(PresignedUploadRequest request);

    @Operation(
            summary = "녹음 파일 재생/다운로드용 Presigned GET URL 발급",
            description = "저장된 녹음 파일을 재생하거나 다운로드할 수 있는 서명된 URL을 발급합니다.\n\n" +
                    "발급된 URL로 `GET` 요청을 보내면 S3에서 파일을 직접 받아올 수 있습니다.\n\n" +
                    "URL 유효시간: 1시간",
            parameters = {
                    @Parameter(name = "recordingId", description = "녹음 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "URL 발급 성공"),
                    @ApiResponse(responseCode = "404", description = "녹음 파일을 찾을 수 없음")
            }
    )
    ResponseEntity<PresignedUrlResponse> getDownloadPresignedUrl(@AuthenticationPrincipal CustomUserDetails userDetails, Long recordingId);

    @Operation(
            summary = "녹음 파일 삭제",
            description = "S3 파일과 DB 레코드를 동시에 삭제합니다.",
            parameters = {
                    @Parameter(name = "recordingId", description = "녹음 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "204", description = "삭제 성공"),
                    @ApiResponse(responseCode = "404", description = "녹음 파일을 찾을 수 없음")
            }
    )
    ResponseEntity<Void> deleteRecording(@AuthenticationPrincipal CustomUserDetails userDetails, Long recordingId);
}