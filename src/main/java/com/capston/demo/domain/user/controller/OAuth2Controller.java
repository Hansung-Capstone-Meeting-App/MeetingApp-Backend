package com.capston.demo.domain.user.controller;


import com.capston.demo.domain.user.dto.OAuthUserInfo;
import com.capston.demo.domain.user.dto.request.SetCalendarDatabaseRequestDto;
import com.capston.demo.domain.user.dto.request.OAuthCodeRequestDto;
import com.capston.demo.domain.user.dto.response.LoginResponseDto;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.global.security.CustomUserDetails;
import com.capston.demo.domain.user.repository.UserNotionAccountRepository;
import com.capston.demo.domain.user.service.AuthService;
import com.capston.demo.domain.user.service.GoogleOAuth2Service;
import com.capston.demo.domain.user.service.NotionOAuth2Service;
import com.capston.demo.domain.user.service.OAuthUserService;
import com.capston.demo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// OAuth2 관련 인증 엔드포인트를 제공하는 컨트롤러 (Google / Notion 공통)
@RestController
@RequestMapping("/api/oauth2")
@RequiredArgsConstructor
@Slf4j
public class OAuth2Controller {

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

    /**
     * Google 인증 URL 반환 (모바일 앱용)
     *
     * @return Google OAuth 인증 URL
     */
    @GetMapping("/google/auth-url")
    public ResponseEntity<Map<String, String>> getGoogleAuthUrl() {
        String authUrl = googleOAuth2Service.getGoogleAuthorizationUrl(); //Google OAuth 인증 URL 생성
        return ResponseEntity.ok(Map.of("authUrl", authUrl)); //Google OAuth 인증 URL 반환
    }

    /**
     * Notion 로그인용 인증 URL (모바일/웹).
     * redirect → GET /notion/callback 에서 code를 즉시 JWT로 교환한다.
     * Google 계정에 Notion을 붙일 때는 /notion/link/auth-url 을 사용할 것.
     */
    @GetMapping("/notion/auth-url")
    public ResponseEntity<Map<String, String>> getNotionAuthUrl() {
        String authUrl = notionOAuth2Service.getNotionAuthorizationUrl(); //로그인용 Notion 인증 URL
        return ResponseEntity.ok(Map.of(
                "authUrl", authUrl, //브라우저에서 열 URL
                "purpose", "login", //용도: Notion으로 MeetingApp 로그인
                "redirectUri", notionOAuth2Service.getLoginRedirectUri() //등록된 redirect (/notion/callback)
        ));
    }

    /**
     * 기존 MeetingApp 계정(Google·이메일 등)에 Notion을 연동할 때 쓰는 인증 URL.
     * redirect → GET /notion/link/callback (code를 JSON으로만 반환, 서버에서 미소비)
     */
    @GetMapping("/notion/link/auth-url")
    public ResponseEntity<Map<String, String>> getNotionLinkAuthUrl() {
        String authUrl = notionOAuth2Service.getNotionLinkAuthorizationUrl(); //연동용 Notion 인증 URL (redirect URI가 login 과 다름)
        return ResponseEntity.ok(Map.of(
                "authUrl", authUrl, //브라우저에서 열 URL
                "purpose", "link", //용도: 이미 로그인한 계정에 Notion 연동
                "redirectUri", notionOAuth2Service.getLinkRedirectUri(), //등록된 redirect (/notion/link/callback)
                "nextStep", "Notion 허용 후 응답 JSON의 code로 POST /api/oauth2/notion/link 호출 (JWT 필요)"
        ));
    }

    /**
     * Notion 계정 연동용 OAuth redirect (브라우저 GET).
     * 로그인용 /notion/callback 과 달리 code를 Notion 토큰으로 교환하지 않고 JSON으로만 반환한다.
     * 실제 연동은 클라이언트가 code를 받아 POST /notion/link (JWT) 로 완료한다.
     */
    @GetMapping("/notion/link/callback")
    public ResponseEntity<Map<String, Object>> notionLinkCallbackGet(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error
    ) {
        if (error != null && !error.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of( //Notion 측에서 error 쿼리로 리다이렉트한 경우
                    "error", error,
                    "message", "Notion OAuth 연동이 거부되었거나 실패했습니다."
            ));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing_code",
                    "message", "authorization code가 없습니다."
            ));
        }
        return ResponseEntity.ok(Map.of( //code는 아직 사용 전 — POST /notion/link 에서 소비
                "code", code,
                "purpose", "link",
                "message", "이 code를 POST /api/oauth2/notion/link body에 넣고, Authorization에 로그인 JWT를 넣으세요.",
                "linkApi", "POST /api/oauth2/notion/link"
        ));
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
            String code = request.getCode(); //인증 코드

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

            return ResponseEntity.ok(response); //JWT 토큰 발급 및 리프레시 토큰 발급
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

            String accessToken = notionOAuth2Service.exchangeCodeForToken(code); //인증 코드를 엑세스 토큰으로 교환
            OAuthUserInfo userInfo = notionOAuth2Service.getUserInfo(accessToken); //엑세스 토큰으로 사용자 정보 조회
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
     * 이미 로그인한 사용자(JWT)의 MeetingApp 계정에 Notion을 연동.
     * code는 반드시 link/auth-url 플로우(/notion/link/callback redirect)에서 받은 것을 사용.
     * (로그인용 /notion/callback 에서 받은 code는 redirect_uri 불일치로 실패함)
     */
    @PostMapping("/notion/link")
    public ResponseEntity<?> linkNotionAccount(@RequestBody OAuthCodeRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); //인증 정보 가져오기
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails)) { //JWT 없음
            return ResponseEntity.status(401).body("Authentication required"); //401 Unauthorized 반환
        }

        String code = request.getCode(); //link/callback redirect 에서 받은 인가 코드
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("Authorization code is required");
        }

        try {
            // link 전용 redirect_uri 로 code 교환 (로그인 callback 과 분리)
            String accessToken = notionOAuth2Service.exchangeCodeForLink(code);
            OAuthUserInfo userInfo = notionOAuth2Service.getUserInfo(accessToken); //Notion 사용자 정보

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            Long userId = userDetails.getUserId(); //JWT에 담긴 Google/이메일 로그인 userId

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

            notionOAuth2Service.linkNotionAccount(user, userInfo, accessToken, userNotionAccountRepository); //user_notion_accounts 저장

            return ResponseEntity.ok(Map.of(
                    "message", "Notion account linked successfully", //연동 성공 메시지
                    "userId", userId, //연동된 MeetingApp 사용자 ID (Google 로그인 userId)
                    "notionName", userInfo.getName() != null ? userInfo.getName() : "" //연동된 Notion 표시 이름
            ));
        } catch (Exception e) {
            log.error("Notion link error: {}", e.getMessage(), e);
            throw e;
        }
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
