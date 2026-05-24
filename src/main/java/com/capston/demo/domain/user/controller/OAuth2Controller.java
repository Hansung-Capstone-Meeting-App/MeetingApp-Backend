package com.capston.demo.domain.user.controller;

import com.capston.demo.domain.user.controllerDocs.OAuth2ControllerDocs;
import com.capston.demo.domain.user.dto.OAuthUserInfo;
import com.capston.demo.domain.user.dto.request.CreateNotionCalendarDatabaseRequestDto;
import com.capston.demo.domain.user.dto.request.SetCalendarDatabaseRequestDto;
import com.capston.demo.domain.user.dto.request.OAuthCodeRequestDto;
import com.capston.demo.domain.user.dto.response.LoginResponseDto;
import com.capston.demo.domain.user.dto.response.NotionCalendarTargetResponse;
import com.capston.demo.domain.user.dto.response.NotionStatusResponse;
import com.capston.demo.domain.user.entity.UserNotionAccount;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.oauth.NotionOAuthFlow;
import com.capston.demo.global.security.CustomUserDetails;
import com.capston.demo.domain.calender.service.NotionCalendarService;
import com.capston.demo.domain.user.repository.UserNotionAccountRepository;
import com.capston.demo.domain.user.service.AuthService;
import com.capston.demo.domain.user.service.GoogleOAuth2Service;
import com.capston.demo.domain.user.service.NotionOAuth2Service;
import com.capston.demo.domain.user.service.OAuthUserService;
import com.capston.demo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

// OAuth2 관련 인증 엔드포인트를 제공하는 컨트롤러 (Google / Notion 공통)
@RestController
@RequestMapping("/api/oauth2")
@RequiredArgsConstructor
@Slf4j
public class OAuth2Controller implements OAuth2ControllerDocs {

    // 각 OAuth 제공자별 액세스 토큰/유저 정보 처리 서비스
    private final GoogleOAuth2Service googleOAuth2Service;
    private final NotionOAuth2Service notionOAuth2Service;
    // OAuth 사용자 정보를 내부 User 엔티티로 연결/생성하는 공통 서비스
    private final OAuthUserService oAuthUserService;
    // JWT 발급 및 로그인 처리 서비스
    private final AuthService authService;
    // 기존 사용자에 Notion 계정을 연결하기 위한 저장소
    private final UserNotionAccountRepository userNotionAccountRepository;
    // 현재 로그인한 사용자를 조회하기 위한 저장소
    private final UserRepository userRepository;
    // Notion 캘린더 DB 검색·일정 생성
    private final NotionCalendarService notionCalendarService;

    /**
     * Google 인증 URL 반환 (모바일 앱용)
     *
     * @return Google OAuth 인증 URL
     */
    @GetMapping("/google/auth-url")
    public ResponseEntity<Map<String, String>> getGoogleAuthUrl() {
        // 프론트/앱이 리다이렉트할 Google 로그인 URL 생성
        String authUrl = googleOAuth2Service.getGoogleAuthorizationUrl();
        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    /**
     * Notion 인증 URL 반환 (모바일 앱용)
     *
     * @return Notion OAuth 인증 URL
     */
    @GetMapping("/notion/auth-url")
    public ResponseEntity<Map<String, String>> getNotionAuthUrl() {
        // auth-url · code 교환 · Notion 콘솔 redirect URI 가 동일한 값 (LOGIN)
        return ResponseEntity.ok(notionAuthUrlResponse(NotionOAuthFlow.LOGIN));
    }

    /**
     * 로그인한 사용자의 Notion 연동용 인증 URL (모바일 앱용)
     * redirect_uri는 application.yml의 link-redirect-uri 와 동일해야 함
     *
     * @return Notion OAuth 인증 URL
     */
    @GetMapping("/notion/link/auth-url")
    public ResponseEntity<Map<String, String>> getNotionLinkAuthUrl() {
        // auth-url · code 교환 · Notion 콘솔 redirect URI 가 동일한 값 (LINK)
        return ResponseEntity.ok(notionAuthUrlResponse(NotionOAuthFlow.LINK));
    }

    /**
     * Notion 연동 OAuth 브라우저 콜백 (GET)
     * JSON/JWT 반환 없이 302로 앱 딥링크에 code 또는 error 전달.
     * 토큰 교환·DB 저장은 앱이 POST /notion/link 로 수행.
     */
    @GetMapping("/notion/link/callback")
    public ResponseEntity<Void> notionLinkCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {
        // meetflow://notion/link?code=... 또는 ?error=...
        String deepLink = notionOAuth2Service.buildLinkDeepLinkRedirect(code, error);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(deepLink))
                .build();
    }

    /**
     * Google OAuth 콜백 처리 (모바일 앱용)
     * 모바일 앱에서 인증 코드를 받아서 JWT 토큰으로 교환
     *
     * @param request 인증 코드를 포함한 요청
     * @return JWT 액세스 토큰 및 리프레시 토큰
     */
    @PostMapping("/google/callback")
    public ResponseEntity<LoginResponseDto> googleCallback(@RequestBody OAuthCodeRequestDto request) {
        try {
            String code = request.getCode();

            if (code == null || code.isEmpty()) {
                // 클라이언트가 인증 코드를 주지 않은 경우
                return ResponseEntity.badRequest().build();
            }

            // 1. 인증 코드를 액세스 토큰으로 교환
            String accessToken = googleOAuth2Service.exchangeCodeForToken(code);

            // 2. 액세스 토큰으로 사용자 정보 조회
            OAuthUserInfo userInfo = googleOAuth2Service.getUserInfo(accessToken);

            // 3. 사용자 처리 (생성 또는 업데이트)
            User user = oAuthUserService.processOAuthUser(userInfo);

            // 4. OAuth 로그인 처리 (JWT 토큰 생성)
            LoginResponseDto response = authService.oauthLogin(user);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("OAuth callback error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Notion OAuth 콜백 처리 (모바일 앱용)
     * 모바일 앱에서 인증 코드를 받아서 JWT 토큰으로 교환
     *
     * @param request 인증 코드를 포함한 요청
     * @return JWT 액세스 토큰 및 리프레시 토큰
     */
    @PostMapping("/notion/callback")
    public ResponseEntity<LoginResponseDto> notionCallback(@RequestBody OAuthCodeRequestDto request) {
        try {
            String code = request.getCode(); //인증 코드

            if (code == null || code.isEmpty()) {
                // 클라이언트가 인증 코드를 주지 않은 경우
                return ResponseEntity.badRequest().build(); //400 Bad Request 반환
            }

            String accessToken = notionOAuth2Service.exchangeCodeForToken(code);
            OAuthUserInfo userInfo = notionOAuth2Service.getUserInfo(accessToken);
            User user = oAuthUserService.processOAuthUser(userInfo); //사용자 정보를 처리하여 User 엔티티로 변환 및 저장
            // 노션으로 로그인 시 해당 유저에 Notion 연동 정보 자동 저장 (캘린더 동기화 등 사용)
            notionOAuth2Service.linkNotionAccount(user, userInfo, accessToken, userNotionAccountRepository); //Notion 계정 연동 정보 저장
            return ResponseEntity.ok(authService.oauthLogin(user)); //JWT 토큰 발급 및 리프레시 토큰 발급
        } catch (Exception e) {
            log.error("Notion OAuth callback error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Google OAuth 콜백 처리 (웹 리다이렉트 GET용)
     * Google은 기본적으로 redirect-uri로 GET 요청을 보냅니다.
     *
     * @param code 인증 코드
     * @return JWT 액세스 토큰 및 리프레시 토큰
     */
    @GetMapping("/google/callback")
    public ResponseEntity<LoginResponseDto> googleCallbackGet(@RequestParam(required = false) String code) {
        if (code == null || code.isBlank()) {
            // Notion/Google에서 쿼리 파라미터로 code를 안 넘겨준 경우
            return ResponseEntity.badRequest().build();
        }
        try {
            String accessToken = googleOAuth2Service.exchangeCodeForToken(code);
            OAuthUserInfo userInfo = googleOAuth2Service.getUserInfo(accessToken);
            User user = oAuthUserService.processOAuthUser(userInfo);
            return ResponseEntity.ok(authService.oauthLogin(user));
        } catch (Exception e) {
            log.error("OAuth GET callback error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Notion OAuth 콜백 처리 (웹 리다이렉트 GET용)
     *
     * @param code 인증 코드
     * @return JWT 액세스 토큰 및 리프레시 토큰
     */
    @GetMapping("/notion/callback")
    public ResponseEntity<LoginResponseDto> notionCallbackGet(@RequestParam(required = false) String code) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String accessToken = notionOAuth2Service.exchangeCodeForToken(code);
            OAuthUserInfo userInfo = notionOAuth2Service.getUserInfo(accessToken);
            User user = oAuthUserService.processOAuthUser(userInfo);
            // 노션으로 로그인 시 해당 유저에 Notion 연동 정보 자동 저장 (캘린더 동기화 등 사용)
            notionOAuth2Service.linkNotionAccount(user, userInfo, accessToken, userNotionAccountRepository);
            return ResponseEntity.ok(authService.oauthLogin(user)); //액세스 토큰, 리프레시 토큰, 토큰 만료기간 리턴
        } catch (Exception e) {
            log.error("Notion OAuth GET callback error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 이미 우리 서비스에 로그인한 사용자가 자신의 노션 계정을 연결할 때 사용하는 엔드포인트
     * (노션으로 로그인해서 새 계정 만드는 것이 아니라, 기존 계정에 노션 계정을 연동)
     */
    @PostMapping("/notion/link")
    public ResponseEntity<?> linkNotionAccount(@RequestBody OAuthCodeRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); //인증 정보 가져오기
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails)) { //인증되지 않은 사용자 또는 예상 타입이 아닌 경우
            return ResponseEntity.status(401).body("Authentication required"); //401 Unauthorized 반환
        }

        String code = request.getCode(); //인증 코드
        if (code == null || code.isBlank()) { //인증 코드가 없는 경우
            return ResponseEntity.badRequest().body("Authorization code is required");
        }

        try {
            // LINK 플로우: auth-url 과 동일한 redirect_uri 로 code 교환
            String accessToken = notionOAuth2Service.exchangeCodeForToken(code, NotionOAuthFlow.LINK);
            OAuthUserInfo userInfo = notionOAuth2Service.getUserInfo(accessToken);

            // 현재 로그인한 사용자 정보에서 userId 조회
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getUserId();

            // userId 기준으로 항상 기존 User를 조회 (없으면 404)
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

            notionOAuth2Service.linkNotionAccount(user, userInfo, accessToken, userNotionAccountRepository);

            return ResponseEntity.ok().body("Notion account linked successfully");
        } catch (Exception e) {
            log.error("Notion link error: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Notion 연동·캘린더·회의록 DB 설정 상태 조회 (설정 화면용).
     * 미연동이어도 200 + linked=false 반환.
     */
    @GetMapping("/notion/status")
    public ResponseEntity<NotionStatusResponse> getNotionStatus() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return ResponseEntity.status(401).build();
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userNotionAccountRepository.findByUser_Id(userDetails.getUserId())
                .map(this::buildNotionStatus)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(NotionStatusResponse.notLinked()));
    }

    /**
     * Notion 연동 후 캘린더로 쓸 수 있는 database 목록 조회 (앱 선택 화면용).
     * JWT 필요, Notion OAuth 연동(access_token) 필요.
     */
    @GetMapping("/notion/calendar-targets")
    public ResponseEntity<?> getNotionCalendarTargets() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();

        return userNotionAccountRepository.findByUser_Id(userId)
                .<ResponseEntity<?>>map(account -> {
                    List<NotionCalendarTargetResponse> targets =
                            notionCalendarService.searchCalendarTargets(account.getAccessToken());
                    return ResponseEntity.ok(targets);
                })
                .orElseGet(() -> ResponseEntity.status(403).body(Map.of("error", "Notion 계정을 먼저 연동해주세요.")));
    }

    /**
     * Notion에 캘린더용 database를 새로 생성하고, 해당 유저의 연동 정보에 자동 등록한다.
     * parentPageId 없으면 연동 워크스페이스의 첫 페이지 아래에 생성한다.
     */
    @PostMapping("/notion/calendar-targets")
    public ResponseEntity<?> createNotionCalendarTarget(
            @RequestBody(required = false) CreateNotionCalendarDatabaseRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();

        String name = request != null ? request.getName() : null;
        String parentPageId = request != null ? request.getParentPageId() : null;

        return userNotionAccountRepository.findByUser_Id(userId)
                .map(account -> {
                    NotionCalendarTargetResponse created = notionCalendarService.createCalendarDatabase(
                            account.getAccessToken(), name, parentPageId);
                    account.setCalendarDatabaseId(created.getId());
                    userNotionAccountRepository.save(account);
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                            "id", created.getId(),
                            "name", created.getName(),
                            "type", created.getType(),
                            "url", created.getUrl() != null ? created.getUrl() : "",
                            "message", "캘린더 데이터베이스가 생성·등록되었습니다.",
                            "calendarConfigured", true
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(403).body(Map.of("error", "Notion 계정을 먼저 연동해주세요.")));
    }

    /**
     * 캘린더 동기화에 사용할 노션 데이터베이스를 등록한다.
     * databaseUrl(노션 DB 페이지 URL) 또는 databaseId를 보내면, 해당 유저의 Notion 연동 정보에 저장된다.
     */
    @PutMapping("/notion/calendar-database")
    public ResponseEntity<?> setCalendarDatabase(@RequestBody SetCalendarDatabaseRequestDto request) { //캘린더 동기화에 사용할 노션 데이터베이스를 등록
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        String databaseId = resolveDatabaseId(request);
        if (databaseId == null || databaseId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "databaseUrl 또는 databaseId를 입력해주세요."));
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();

        return userNotionAccountRepository.findByUser_Id(userId)
                .map(account -> {
                    account.setCalendarDatabaseId(databaseId);
                    userNotionAccountRepository.save(account);
                    return ResponseEntity.ok().body(Map.of(
                            "message", "캘린더 데이터베이스가 등록되었습니다.",
                            "calendarDatabaseId", databaseId
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(403).body(Map.of("error", "Notion 계정을 먼저 연동해주세요.")));
    }

    /**
     * Notion에 회의록 export용 database를 새로 생성하고, 해당 유저의 연동 정보에 자동 등록한다.
     * parentPageId 없으면 연동 워크스페이스의 첫 페이지 아래에 생성한다.
     */
    @PostMapping("/notion/meeting-notes-targets")
    public ResponseEntity<?> createNotionMeetingNotesTarget(
            @RequestBody(required = false) CreateNotionCalendarDatabaseRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();

        String name = request != null ? request.getName() : null;
        String parentPageId = request != null ? request.getParentPageId() : null;

        return userNotionAccountRepository.findByUser_Id(userId)
                .map(account -> {
                    NotionCalendarTargetResponse created = notionCalendarService.createMeetingNotesDatabase(
                            account.getAccessToken(), name, parentPageId);
                    account.setMeetingNotesDatabaseId(created.getId());
                    userNotionAccountRepository.save(account);
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                            "id", created.getId(),
                            "name", created.getName(),
                            "type", created.getType(),
                            "url", created.getUrl() != null ? created.getUrl() : "",
                            "message", "회의록 데이터베이스가 생성·등록되었습니다.",
                            "meetingNotesConfigured", true
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(403).body(Map.of("error", "Notion 계정을 먼저 연동해주세요.")));
    }

    /**
     * 회의록 export에 사용할 노션 데이터베이스를 등록한다.
     */
    @PutMapping("/notion/meeting-notes-database")
    public ResponseEntity<?> setMeetingNotesDatabase(@RequestBody SetCalendarDatabaseRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        String databaseId = resolveDatabaseId(request);
        if (databaseId == null || databaseId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "databaseUrl 또는 databaseId를 입력해주세요."));
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();

        return userNotionAccountRepository.findByUser_Id(userId)
                .map(account -> {
                    account.setMeetingNotesDatabaseId(databaseId);
                    userNotionAccountRepository.save(account);
                    return ResponseEntity.ok().body(Map.of(
                            "message", "회의록 데이터베이스가 등록되었습니다.",
                            "meetingNotesDatabaseId", databaseId
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(403).body(Map.of("error", "Notion 계정을 먼저 연동해주세요.")));
    }

    private NotionStatusResponse buildNotionStatus(UserNotionAccount account) { //Notion 상태 응답 생성
        String calendarDatabaseId = account.getCalendarDatabaseId(); //캘린더 데이터베이스 ID 추출
        boolean calendarConfigured = calendarDatabaseId != null && !calendarDatabaseId.isBlank(); //캘린더 데이터베이스 설정 여부 확인
        String calendarName = null;
        if (calendarConfigured) {
            calendarName = notionCalendarService.fetchDatabaseName(account.getAccessToken(), calendarDatabaseId); //캘린더 데이터베이스 이름 추출
        }

        String meetingNotesDatabaseId = account.getMeetingNotesDatabaseId(); //회의록 데이터베이스 ID 추출
        boolean meetingNotesConfigured = meetingNotesDatabaseId != null && !meetingNotesDatabaseId.isBlank(); //회의록 데이터베이스 설정 여부 확인
        String meetingNotesName = null;
        if (meetingNotesConfigured) {
            meetingNotesName = notionCalendarService.fetchDatabaseName(account.getAccessToken(), meetingNotesDatabaseId); //회의록 데이터베이스 이름 추출
        }

        return new NotionStatusResponse( //Notion 상태 응답 생성
                true, //연동 여부 설정
                calendarConfigured, //캘린더 데이터베이스 설정 여부 설정
                account.getNotionName(), //Notion 이름 설정
                calendarName, //캘린더 데이터베이스 이름 설정
                meetingNotesConfigured, //회의록 데이터베이스 설정 여부 설정
                meetingNotesName //회의록 데이터베이스 이름 설정
        ); //Notion 상태 응답 생성
    }

    /**
     * Notion auth-url 응답 — redirectUri 를 함께 내려 Notion Integration 등록 URI 와 대조 가능
     */
    private Map<String, String> notionAuthUrlResponse(NotionOAuthFlow flow) {
        return Map.of(
                "authUrl", notionOAuth2Service.getAuthorizationUrl(flow),
                "redirectUri", notionOAuth2Service.resolveRedirectUri(flow)
        );
    }

    /** databaseId가 있으면 그대로, 없으면 databaseUrl에서 마지막 path 세그먼트로 ID 추출 */
    private String resolveDatabaseId(SetCalendarDatabaseRequestDto request) {
        if (request.getDatabaseId() != null && !request.getDatabaseId().isBlank()) {
            return request.getDatabaseId().trim();
        }
        if (request.getDatabaseUrl() == null || request.getDatabaseUrl().isBlank()) {
            return null;
        }
        String url = request.getDatabaseUrl().trim();
        int q = url.indexOf('?');
        if (q >= 0) {
            url = url.substring(0, q);
        }
        String[] segments = url.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            String s = segments[i].trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }
}
