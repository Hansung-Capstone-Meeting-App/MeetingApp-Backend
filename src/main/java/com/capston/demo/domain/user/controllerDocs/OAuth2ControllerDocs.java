package com.capston.demo.domain.user.controllerDocs;

import com.capston.demo.domain.user.dto.request.CreateNotionCalendarDatabaseRequestDto;
import com.capston.demo.domain.user.dto.request.OAuthCodeRequestDto;
import com.capston.demo.domain.user.dto.request.SetCalendarDatabaseRequestDto;
import com.capston.demo.domain.user.dto.response.LoginResponseDto;
import com.capston.demo.domain.user.dto.response.NotionStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@Tag(name = "OAuth2", description = "Google/Notion OAuth 로그인·연동 및 Notion 캘린더/회의록 DB 설정 API")
public interface OAuth2ControllerDocs {

    @Operation(
            summary = "Google OAuth 인증 URL 조회",
            description = "모바일/웹 앱이 리다이렉트할 Google 로그인 URL을 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<Map<String, String>> getGoogleAuthUrl();

    @Operation(
            summary = "Notion OAuth 인증 URL 조회 (로그인)",
            description = "Notion으로 로그인(신규/기존 계정)할 때 사용하는 인증 URL과 redirectUri를 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<Map<String, String>> getNotionAuthUrl();

    @Operation(
            summary = "Notion OAuth 인증 URL 조회 (기존 계정 연동)",
            description = "이미 로그인한 사용자가 Notion 계정을 연동할 때 사용합니다. LINK 플로우 redirectUri를 사용합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<Map<String, String>> getNotionLinkAuthUrl();

    @Operation(
            summary = "Notion 연동 OAuth 브라우저 콜백",
            description = """
                    Notion 연동(LINK) 플로우의 브라우저 콜백입니다.
                    JSON/JWT를 반환하지 않고 302로 앱 딥링크(meetflow://notion/link)에 code 또는 error를 전달합니다.
                    토큰 교환·DB 저장은 앱이 POST /api/oauth2/notion/link 로 수행합니다.
                    """,
            parameters = {
                    @Parameter(name = "code", description = "Notion 인증 코드"),
                    @Parameter(name = "error", description = "OAuth 오류 코드")
            },
            responses = {
                    @ApiResponse(responseCode = "302", description = "앱 딥링크로 리다이렉트")
            }
    )
    ResponseEntity<String> notionLinkCallback(String code, String error, String state);

    @Operation(
            summary = "Google OAuth 콜백 (POST)",
            description = "모바일 앱에서 받은 인증 코드로 JWT를 발급합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OAuthCodeRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "code": "4/0AeanS..."
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "로그인 성공"),
                    @ApiResponse(responseCode = "400", description = "인증 코드 누락")
            }
    )
    ResponseEntity<LoginResponseDto> googleCallback(OAuthCodeRequestDto request);

    @Operation(
            summary = "Notion OAuth 콜백 (POST, 로그인)",
            description = "Notion 인증 코드로 로그인하고, Notion 연동 정보를 자동 저장한 뒤 JWT를 발급합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OAuthCodeRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "code": "abc123..."
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "로그인 성공"),
                    @ApiResponse(responseCode = "400", description = "인증 코드 누락")
            }
    )
    ResponseEntity<LoginResponseDto> notionCallback(OAuthCodeRequestDto request);

    @Operation(
            summary = "Google OAuth 콜백 (GET)",
            description = "Google redirect URI로 들어오는 GET 콜백입니다. 쿼리 파라미터 code로 JWT를 발급합니다.",
            parameters = {
                    @Parameter(name = "code", description = "Google 인증 코드", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "로그인 성공"),
                    @ApiResponse(responseCode = "400", description = "인증 코드 누락")
            }
    )
    ResponseEntity<LoginResponseDto> googleCallbackGet(String code);

    @Operation(
            summary = "Notion OAuth 콜백 (GET, 로그인)",
            description = "Notion redirect URI로 들어오는 GET 콜백입니다. 로그인 시 Notion 연동 정보를 자동 저장합니다.",
            parameters = {
                    @Parameter(name = "code", description = "Notion 인증 코드", required = true)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "로그인 성공"),
                    @ApiResponse(responseCode = "400", description = "인증 코드 누락")
            }
    )
    ResponseEntity<LoginResponseDto> notionCallbackGet(String code);

    @Operation(
            summary = "Notion 계정 연동 (기존 로그인 사용자)",
            description = "JWT 인증된 사용자에게 Notion 계정을 연결합니다. LINK 플로우의 인증 코드가 필요합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OAuthCodeRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "code": "abc123..."
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "연동 성공"),
                    @ApiResponse(responseCode = "400", description = "인증 코드 누락"),
                    @ApiResponse(responseCode = "401", description = "JWT 인증 필요")
            }
    )
    ResponseEntity<?> linkNotionAccount(OAuthCodeRequestDto request);

    @Operation(
            summary = "Notion 연동·DB 설정 상태 조회",
            description = "설정 화면용. Notion 미연동이어도 200 + linked=false를 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "401", description = "JWT 인증 필요")
            }
    )
    ResponseEntity<NotionStatusResponse> getNotionStatus();

    @Operation(
            summary = "Notion 캘린더 DB 후보 목록 조회",
            description = "Notion OAuth 연동 후, 캘린더로 사용할 수 있는 database 목록을 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "401", description = "JWT 인증 필요"),
                    @ApiResponse(responseCode = "403", description = "Notion 미연동")
            }
    )
    ResponseEntity<?> getNotionCalendarTargets();

    @Operation(
            summary = "Notion 캘린더 DB 생성·등록",
            description = "Notion에 캘린더용 database를 새로 만들고, 해당 유저 연동 정보에 자동 등록합니다.",
            requestBody = @RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateNotionCalendarDatabaseRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "name": "Meetflow 일정",
                                              "parentPageId": "page-id-optional"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "생성·등록 성공"),
                    @ApiResponse(responseCode = "401", description = "JWT 인증 필요"),
                    @ApiResponse(responseCode = "403", description = "Notion 미연동")
            }
    )
    ResponseEntity<?> createNotionCalendarTarget(CreateNotionCalendarDatabaseRequestDto request);

    @Operation(
            summary = "Notion 캘린더 DB 등록",
            description = "기존 Notion database URL 또는 ID를 캘린더 동기화 대상으로 등록합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SetCalendarDatabaseRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "databaseUrl": "https://www.notion.so/workspace/3230e99b88308071b256df5dfcc8bfe7"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "등록 성공"),
                    @ApiResponse(responseCode = "400", description = "databaseUrl/databaseId 누락"),
                    @ApiResponse(responseCode = "401", description = "JWT 인증 필요"),
                    @ApiResponse(responseCode = "403", description = "Notion 미연동")
            }
    )
    ResponseEntity<?> setCalendarDatabase(SetCalendarDatabaseRequestDto request);

    @Operation(
            summary = "Notion 회의록 DB 생성·등록",
            description = "Notion에 회의록 export용 database를 새로 만들고, 해당 유저 연동 정보에 자동 등록합니다.",
            requestBody = @RequestBody(
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateNotionCalendarDatabaseRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "name": "Meetflow 회의록",
                                              "parentPageId": "page-id-optional"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "생성·등록 성공"),
                    @ApiResponse(responseCode = "401", description = "JWT 인증 필요"),
                    @ApiResponse(responseCode = "403", description = "Notion 미연동")
            }
    )
    ResponseEntity<?> createNotionMeetingNotesTarget(CreateNotionCalendarDatabaseRequestDto request);

    @Operation(
            summary = "Notion 회의록 DB 등록",
            description = "기존 Notion database URL 또는 ID를 회의록 export 대상으로 등록합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SetCalendarDatabaseRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "databaseId": "3230e99b88308071b256df5dfcc8bfe7"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "등록 성공"),
                    @ApiResponse(responseCode = "400", description = "databaseUrl/databaseId 누락"),
                    @ApiResponse(responseCode = "401", description = "JWT 인증 필요"),
                    @ApiResponse(responseCode = "403", description = "Notion 미연동")
            }
    )
    ResponseEntity<?> setMeetingNotesDatabase(SetCalendarDatabaseRequestDto request);
}
