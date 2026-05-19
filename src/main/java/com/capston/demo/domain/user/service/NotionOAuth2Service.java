package com.capston.demo.domain.user.service;

import com.capston.demo.domain.user.dto.OAuthUserInfo;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.entity.UserNotionAccount;
import com.capston.demo.domain.user.repository.UserNotionAccountRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

// Notion OAuth2 전용 토큰/유저 정보 처리를 담당하는 서비스
@Service
@RequiredArgsConstructor
@Slf4j
public class NotionOAuth2Service {

    private final RestTemplate restTemplate;

    // OAuth redirect URI 치환용 서버 base URL (application.yml: app.oauth.base-url)
    @Value("${app.oauth.base-url:http://localhost:8080}")
    private String oauthBaseUrl;

    // Notion OAuth 클라이언트 ID
    @Value("${spring.security.oauth2.client.registration.notion.client-id}")
    private String clientId;

    // Notion OAuth 클라이언트 시크릿
    @Value("${spring.security.oauth2.client.registration.notion.client-secret}")
    private String clientSecret;

    // Notion 로그인용 redirect URI 템플릿 (→ /api/oauth2/notion/callback)
    @Value("${spring.security.oauth2.client.registration.notion.redirect-uri}")
    private String loginRedirectUriTemplate;

    // Notion 계정 연동용 redirect URI 템플릿 (→ /api/oauth2/notion/link/callback)
    @Value("${spring.security.oauth2.client.registration.notion.link-redirect-uri}")
    private String linkRedirectUriTemplate;

    // 요청할 Notion 권한 범위
    @Value("${spring.security.oauth2.client.registration.notion.scope:read_user}")
    private String scope;

    // Notion 인증 페이지 URL
    @Value("${spring.security.oauth2.client.provider.notion.authorization-uri}")
    private String authorizationUri;

    // Notion 토큰 발급 엔드포인트
    @Value("${spring.security.oauth2.client.provider.notion.token-uri}")
    private String tokenUri;

    // Notion 사용자 정보 조회 엔드포인트
    @Value("${spring.security.oauth2.client.provider.notion.user-info-uri}")
    private String userInfoUri;

    // Notion API 버전 헤더 값
    @Value("${spring.security.oauth2.client.provider.notion.notion-version:2022-06-28}")
    private String notionVersion;

    /**
     * Notion 로그인용 인증 URL.
     * redirect 후 GET /notion/callback 에서 code를 즉시 JWT로 교환한다.
     */
    public String getNotionAuthorizationUrl() {
        String finalRedirectUri = resolveRedirectUri(loginRedirectUriTemplate); //로그인용 redirect URI
        return buildAuthorizationUrl(finalRedirectUri);
    }

    /**
     * 기존 MeetingApp 계정(Google 등)에 Notion을 연동할 때 쓰는 인증 URL.
     * redirect 후 GET /notion/link/callback 에서 code를 JSON으로만 반환한다(서버에서 미소비).
     */
    public String getNotionLinkAuthorizationUrl() {
        String finalRedirectUri = resolveRedirectUri(linkRedirectUriTemplate); //연동용 redirect URI
        return buildAuthorizationUrl(finalRedirectUri);
    }

    // Swagger/문서용: 로그인 redirect URI 실제 값
    public String getLoginRedirectUri() {
        return resolveRedirectUri(loginRedirectUriTemplate);
    }

    // Swagger/문서용: 연동 redirect URI 실제 값
    public String getLinkRedirectUri() {
        return resolveRedirectUri(linkRedirectUriTemplate);
    }

    // Notion authorize URL 공통 생성 (redirect_uri만 다름)
    private String buildAuthorizationUrl(String finalRedirectUri) {
        return UriComponentsBuilder.fromHttpUrl(authorizationUri) //Notion 인증 페이지 URL
                .queryParam("client_id", clientId) //Notion OAuth 클라이언트 ID
                .queryParam("redirect_uri", finalRedirectUri) //Notion 리다이렉트 URI
                .queryParam("response_type", "code") //인증 코드 발급 요청
                .queryParam("owner", "user") //사용자 인증 요청
                .queryParam("scope", scope) //요청할 Notion 권한 범위
                .toUriString(); //URL 문자열로 변환
    }

    /**
     * Notion 로그인 callback용 code 교환.
     * auth-url과 동일한 redirect_uri(/notion/callback)를 사용해야 한다.
     */
    public String exchangeCodeForToken(String code) { //로그인용 code 교환
        return exchangeCodeForToken(code, resolveRedirectUri(loginRedirectUriTemplate)); //로그인용 redirect URI 치환
    }

    /**
     * POST /notion/link 용 code 교환.
     * link/auth-url과 동일한 redirect_uri(/notion/link/callback)를 사용해야 한다.
     */
    public String exchangeCodeForLink(String code) { //연동용 code 교환
        return exchangeCodeForToken(code, resolveRedirectUri(linkRedirectUriTemplate)); //연동용 redirect URI 치환
    }

    // 인가 코드(code)를 Notion 액세스 토큰으로 교환 (redirect_uri는 호출 목적에 따라 다름)
    private String exchangeCodeForToken(String code, String redirectUri) {
        return executeWithRetry(() -> {
            try {
                HttpHeaders headers = new HttpHeaders(); //HTTP 헤더 생성
                headers.setContentType(MediaType.APPLICATION_JSON); //Content-Type을 JSON으로 설정
                headers.setBasicAuth(clientId, clientSecret); //Notion은 Basic Auth로 client_id/secret 전달

                Map<String, Object> body = Map.of( //요청 바디 생성
                        "grant_type", "authorization_code",
                        "code", code,
                        "redirect_uri", redirectUri //auth-url 요청 시 넣었던 redirect_uri와 동일해야 함
                );

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers); //HTTP 요청 엔티티 생성
                ResponseEntity<Map> response = restTemplate.exchange( //Notion 토큰 엔드포인트 호출
                        tokenUri,
                        HttpMethod.POST,
                        request,
                        Map.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Object token = response.getBody().get("access_token"); //엑세스 토큰 추출
                    if (token instanceof String s && !s.isBlank()) {
                        return s;
                    }
                }

                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Notion token exchange error: {}", e.getMessage());
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED, e);
            }
        });
    }

    // Notion 액세스 토큰으로 현재 사용자(me) 정보 조회
    public OAuthUserInfo getUserInfo(String accessToken) {
        return executeWithRetry(() -> {
            try {
                HttpHeaders headers = new HttpHeaders(); //HTTP 헤더 생성
                headers.setBearerAuth(accessToken); //Bearer Auth 설정
                headers.set("Notion-Version", notionVersion); //Notion API 버전 헤더 설정

                HttpEntity<Void> request = new HttpEntity<>(headers); //HTTP 요청 엔티티 생성
                ResponseEntity<Map> response = restTemplate.exchange( //Notion 사용자 정보 조회 엔드포인트 호출
                        userInfoUri,
                        HttpMethod.GET,
                        request,
                        Map.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map<String, Object> body = response.getBody();
                    return parseOAuthUserInfo(body);
                }

                throw new BusinessException(ErrorCode.OAUTH_USER_INFO_FAILED);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Notion user info error: {}", e.getMessage());
                throw new BusinessException(ErrorCode.OAUTH_USER_INFO_FAILED, e);
            }
        });
    }

    // 우리 서비스 User(JWT 로그인 사용자)와 Notion 계정을 연동하거나 갱신 → user_notion_accounts 저장
    public void linkNotionAccount(User user,
                                  OAuthUserInfo userInfo,
                                  String accessToken,
                                  UserNotionAccountRepository repository) {
        Long userId = user.getId();
        repository.findByUser_Id(userId) //user_id 기준 조회 (동일 유저 중복 행 방지)
                .ifPresentOrElse(
                        existing -> { //기존 연동 정보가 있을 경우 갱신
                            existing.setAccessToken(accessToken); //Notion API 호출용 토큰 갱신
                            existing.setNotionUserId(userInfo.getProviderId());
                            existing.setNotionName(userInfo.getName());
                            existing.setLinkedAt(java.time.LocalDateTime.now());
                            repository.save(existing);
                        },
                        () -> { //기존 연동 정보가 없을 경우 새로 생성
                            UserNotionAccount account = new UserNotionAccount(); //UserNotionAccount 객체 생성
                            account.setUser(user); //사용자 연동
                            account.setNotionUserId(userInfo.getProviderId()); //Notion 사용자 ID
                            account.setNotionName(userInfo.getName()); //Notion 사용자 이름
                            account.setAccessToken(accessToken); //Notion API 호출용 토큰
                            repository.save(account); //저장
                        }
                );
    }

    // application.yml의 {baseUrl}을 실제 서버 주소로 치환
    private String resolveRedirectUri(String template) {
        String base = oauthBaseUrl == null ? "http://localhost:8080" : oauthBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return template.replace("{baseUrl}", base);
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ResourceAccessException e) {
            log.warn("Network error occurred, retrying once: {}", e.getMessage());
            return operation.get();
        }
    }

    /**
     * OAuth 토큰의 /users/me 응답 파싱.
     * OAuth 토큰은 봇(bot) 사용자를 가리키므로 최상위 name은 연동 앱 이름이다.
     * 실제 로그인한 사람의 이름·이메일은 bot.owner.user 에 있다.
     */
    private OAuthUserInfo parseOAuthUserInfo(Map<String, Object> body) {
        String id = asString(body.get("id"));
        String name = asString(body.get("name"));
        String avatarUrl = asString(body.get("avatar_url"));
        String email = null;

        String type = asString(body.get("type"));
        if ("person".equals(type)) {
            Object personObj = body.get("person");
            if (personObj instanceof Map<?, ?> person) {
                email = asString(person.get("email"));
            }
        } else if ("bot".equals(type)) {
            Object botObj = body.get("bot");
            if (botObj instanceof Map<?, ?> bot) {
                Object ownerObj = bot.get("owner");
                if (ownerObj instanceof Map<?, ?> owner && "user".equals(asString(owner.get("type")))) {
                    Object userObj = owner.get("user");
                    if (userObj instanceof Map<?, ?> ownerUser) {
                        String ownerId = asString(ownerUser.get("id"));
                        String ownerName = asString(ownerUser.get("name"));
                        String ownerAvatar = asString(ownerUser.get("avatar_url"));
                        if (ownerId != null && !ownerId.isBlank()) {
                            id = ownerId;
                        }
                        if (ownerName != null && !ownerName.isBlank()) {
                            name = ownerName;
                        }
                        if (ownerAvatar != null && !ownerAvatar.isBlank()) {
                            avatarUrl = ownerAvatar;
                        }
                        Object personObj = ownerUser.get("person");
                        if (personObj instanceof Map<?, ?> person) {
                            email = asString(person.get("email"));
                        }
                    }
                }
            }
        }

        return OAuthUserInfo.builder()
                .provider("notion")
                .providerId(id)
                .email(email)
                .name(name != null && !name.isBlank() ? name : "Notion User")
                .picture(avatarUrl)
                .build();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
