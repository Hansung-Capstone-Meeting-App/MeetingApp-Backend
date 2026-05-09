package com.capston.demo.domain.ai.controllerDocs;

import com.capston.demo.domain.ai.dto.response.GeminiAnalyzeResponse;
import com.capston.demo.domain.ai.dto.response.TranscribeResponse;
import com.capston.demo.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "AI Analysis", description = "STT 전사 및 Gemini AI 분석 API")
public interface MeetingAnalysisControllerDocs {

    @Operation(
            summary = "STT 전사 (AssemblyAI)",
            description = "S3에 저장된 녹음 파일을 AssemblyAI로 전사합니다. 화자 분리가 자동으로 수행됩니다.\n\n" +
                    "완료 후 MongoDB에 트랜스크립트가 저장되며 `transcriptId`가 반환됩니다.\n\n" +
                    "이후 화자 매핑 → Gemini 분석 순서로 진행합니다.",
            parameters = {
                    @Parameter(name = "meetingId", description = "회의 ID", example = "1", required = true),
                    @Parameter(name = "recordingId", description = "녹음 ID", example = "1", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "전사 성공 — transcriptId 포함"),
                    @ApiResponse(responseCode = "404", description = "회의 또는 녹음 파일을 찾을 수 없음")
            }
    )
    ResponseEntity<TranscribeResponse> transcribe(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Long meetingId,
            Long recordingId);

    @Operation(
            summary = "Gemini AI 분석",
            description = "트랜스크립트에 대해 Gemini AI 분석을 실행합니다. 화자 매핑 완료 후 호출하세요.\n\n" +
                    "**분석 결과**\n" +
                    "- 회의 요약\n" +
                    "- 키워드 추출\n" +
                    "- 할 일(Task) 자동 추출 → DB 저장\n" +
                    "- 이벤트(Event) 자동 추출 → DB 저장",
            parameters = {
                    @Parameter(name = "transcriptId", description = "트랜스크립트 ID (MongoDB ObjectId)", example = "6634c1a2f3e4b12345678901", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "분석 성공"),
                    @ApiResponse(responseCode = "404", description = "트랜스크립트를 찾을 수 없음")
            }
    )
    ResponseEntity<GeminiAnalyzeResponse> geminiAnalyze(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            String transcriptId);
}
