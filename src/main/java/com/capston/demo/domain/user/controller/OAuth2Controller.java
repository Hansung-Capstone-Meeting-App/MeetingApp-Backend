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
        // 프론트/앱이 리다이렉트할 Notion 로그인 URL 생성
        String authUrl = notionOAuth2Service.getNotionAuthorizationUrl();
        return ResponseEntity.ok(Map.of("authUrl", authUrl)); //Notion 인증 URL 반환
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
            String accessToken = notionOAuth2Service.exchangeCodeForToken(code); //인증 코드를 엑세스 토큰으로 교환
            OAuthUserInfo userInfo = notionOAuth2Service.getUserInfo(accessToken); //엑세스 토큰으로 사용자 정보 조회

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
