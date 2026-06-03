package com.capston.demo.domain.user.controllerDocs;

import com.capston.demo.domain.user.dto.request.CreateNotionCalendarDatabaseRequestDto;
import com.capston.demo.domain.user.dto.request.OAuthCodeRequestDto;
import com.capston.demo.domain.user.dto.request.SetCalendarDatabaseRequestDto;
import com.capston.demo.domain.user.dto.request.SetNotionRootPageRequestDto;
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
            description = """
                    Google 로그인 URL과 redirectUri 반환.
                    - client=web(기본): /google/callback (GET JWT)
                    - client=mobile: /google/callback/mobile (HTML → meetflow://oauth/google)
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<Map<String, String>> getGoogleAuthUrl(
            @Parameter(description = "web(기본) | mobile") String client);

    @Operation(
            summary = "Notion OAuth 인증 URL 조회 (로그인)",
            description = """
                    Notion 로그인 인증 URL과 redirectUri를 반환합니다.
                    - client=web: /notion/callback (GET JWT)
                    - client=mobile: /notion/callback/mobile (HTML → meetflow://oauth/notion)
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<Map<String, String>> getNotionAuthUrl(
            @Parameter(description = "web(기본) | mobile") String client);

    @Operation(
            summary = "Notion OAuth 인증 URL 조회 (기존 계정 연동)",
            description = """
                    이미 로그인한 사용자의 Notion 연동용 인증 URL.
                    - client=web: /notion/link/callback
                    - client=mobile: /notion/link/callback/mobile
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    ResponseEntity<Map<String, String>> getNotionLinkAuthUrl(
            @Parameter(description = "web(기본) | mobile") String client);

    @Operation(
            summary = "Notion 연동 OAuth 웹 콜백 (Swagger용)",
            description = """
                    Notion 연동(LINK) 웹 redirect URI 콜백. code를 JSON으로 반환합니다.
                    연동 완료는 POST /api/oauth2/notion/link (JWT + client=web) 로 수행합니다.
                    """,
            parameters = {
                    @Parameter(name = "code", description = "Notion 인증 코드"),
                    @Parameter(name = "error", description = "OAuth 오류 코드")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "code JSON"),
                    @ApiResponse(responseCode = "400", description = "code/error 누락")
            }
    )
    ResponseEntity<Map<String, String>> notionLinkCallbackWeb(String code, String error);

    @Operation(
            summary = "Notion 연동 OAuth 모바일 콜백",
            description = "Notion LINK mobile redirect URI. HTML 브릿지로 meetflow://notion/link 에 code 전달.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "HTML 브릿지")
            }
    )
    ResponseEntity<String> notionLinkCallbackMobile(String code, String error);

    @Operation(
            summary = "Google OAuth 모바일 콜백",
            description = "Google mobile redirect URI. HTML 브릿지로 meetflow://oauth/google 에 code 전달.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "HTML 브릿지")
            }
    )
    ResponseEntity<String> googleCallbackMobile(String code, String error);

    @Operation(
            summary = "Notion OAuth 모바일 콜백 (로그인)",
            description = "Notion LOGIN mobile redirect URI. HTML 브릿지로 meetflow://oauth/notion 에 code 전달.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "HTML 브릿지")
            }
    )
    ResponseEntity<String> notionCallbackMobile(String code, String error);

    @Operation(
            summary = "Google OAuth 콜백 (POST)",
            description = """
                    인증 코드로 JWT 발급. client=mobile(기본) | web — auth-url 과 동일한 redirect_uri 로 code 교환.
                    """,
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OAuthCodeRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "code": "4/0AeanS...",
                                              "client": "mobile"
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
            summary = "Google OAuth 콜백 (GET, 웹)",
            description = "Google web redirect URI. 쿼리 code로 JWT 발급.",
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
            summary = "Notion OAuth 콜백 (GET, 웹 로그인)",
            description = "Notion web redirect URI. 로그인 시 Notion 연동 정보 자동 저장.",
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
            summary = "Notion 최상위 page 후보 목록 조회",
            description = """
                    Meetflow 캘린더·회의록 DB를 둘 workspace 최상위 page 목록을 반환합니다.
                    - DB 행(일정 page)은 제외
                    - 선택한 id는 PUT /notion/root-page 로 저장한 뒤 DB 생성 API 호출
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "401", description = "JWT 인증 필요"),
                    @ApiResponse(responseCode = "403", description = "Notion 미연동")
            }
    )
    ResponseEntity<?> getNotionRootPages();

    @Operation(
            summary = "Notion 최상위 page 저장",
            description = """
                    GET /notion/root-pages 에서 선택한 page ID를 user_notion_accounts.root_page_id 에 저장합니다.
                    이후 POST calendar-targets / meeting-notes-targets 에서 parentPageId 생략 시 이 값을 사용합니다.
                    """,
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SetNotionRootPageRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "parentPageId": "page-uuid-from-root-pages"
                                            }
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "저장 성공"),
                    @ApiResponse(responseCode = "400", description = "parentPageId 누락 또는 최상위 page 아님"),
                    @ApiResponse(responseCode = "401", description = "JWT 인증 필요"),
                    @ApiResponse(responseCode = "403", description = "Notion 미연동")
            }
    )
    ResponseEntity<?> setNotionRootPage(SetNotionRootPageRequestDto request);

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
            description = """
                    기존 Notion database URL 또는 ID를 캘린더 동기화 대상으로 등록합니다.
                    - `resetExistingEventLinks=true`: 내 워크스페이스 일정의 notion_page_id·notion_synced_at 초기화
                    - 등록 DB ID가 이전과 다르면 위 옵션 없이도 자동 초기화 (다음 동기화 시 새 DB에 페이지 생성)
                    """,
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SetCalendarDatabaseRequestDto.class),
                            examples = @ExampleObject(
                                    name = "요청 예시",
                                    value = """
                                            {
                                              "databaseUrl": "https://www.notion.so/workspace/3230e99b88308071b256df5dfcc8bfe7",
                                              "resetExistingEventLinks": true
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
