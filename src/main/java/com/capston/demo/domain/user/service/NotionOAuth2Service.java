package com.capston.demo.domain.user.service;

import com.capston.demo.domain.user.dto.OAuthUserInfo;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.entity.UserNotionAccount;
import com.capston.demo.domain.user.oauth.OAuthCallbackBridgeHtml;
import com.capston.demo.domain.user.oauth.OAuthClientType;
import com.capston.demo.domain.user.oauth.NotionOAuthFlow;
import com.capston.demo.domain.user.repository.UserNotionAccountRepository;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// Notion OAuth2 전용 토큰/유저 정보 처리를 담당하는 서비스
@Service
@RequiredArgsConstructor
@Slf4j
public class NotionOAuth2Service {

    private final RestTemplate restTemplate;

    // Notion OAuth 클라이언트 ID
    @Value("${spring.security.oauth2.client.registration.notion.client-id}")
    private String clientId;

    // Notion OAuth 클라이언트 시크릿
    @Value("${spring.security.oauth2.client.registration.notion.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.notion.redirect-uri:{baseUrl}/api/oauth2/notion/callback}")
    private String loginRedirectUriTemplate;

    @Value("${spring.security.oauth2.client.registration.notion.mobile-redirect-uri:{baseUrl}/api/oauth2/notion/callback/mobile}")
    private String loginMobileRedirectUriTemplate;

    @Value("${spring.security.oauth2.client.registration.notion.link-redirect-uri:{baseUrl}/api/oauth2/notion/link/callback}")
    private String linkRedirectUriTemplate;

    @Value("${spring.security.oauth2.client.registration.notion.link-mobile-redirect-uri:{baseUrl}/api/oauth2/notion/link/callback/mobile}")
    private String linkMobileRedirectUriTemplate;

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

    // application.yml app.oauth.base-url — {baseUrl} 치환용
    @Value("${app.oauth.base-url:http://localhost:8080}")
    private String oauthBaseUrl;

    @Value("${app.oauth.mobile.notion-login-deep-link:meetflow://oauth/notion}")
    private String notionLoginMobileDeepLink;

    @Value("${app.notion.link-deep-link:meetflow://notion/link}")
    private String linkDeepLink;

    @PostConstruct
    void logResolvedRedirectUris() {
        log.info("Notion OAuth LOGIN WEB redirect_uri={}", resolveRedirectUri(NotionOAuthFlow.LOGIN, OAuthClientType.WEB));
        log.info("Notion OAuth LOGIN MOBILE redirect_uri={}", resolveRedirectUri(NotionOAuthFlow.LOGIN, OAuthClientType.MOBILE));
        log.info("Notion OAuth LINK WEB redirect_uri={}", resolveRedirectUri(NotionOAuthFlow.LINK, OAuthClientType.WEB));
        log.info("Notion OAuth LINK MOBILE redirect_uri={}", resolveRedirectUri(NotionOAuthFlow.LINK, OAuthClientType.MOBILE));
    }

    public String resolveRedirectUri(NotionOAuthFlow flow) {
        return resolveRedirectUri(flow, OAuthClientType.WEB);
    }

    public String resolveRedirectUri(NotionOAuthFlow flow, OAuthClientType clientType) {
        String template = switch (flow) {
            case LOGIN -> clientType == OAuthClientType.MOBILE
                    ? loginMobileRedirectUriTemplate
                    : loginRedirectUriTemplate;
            case LINK -> clientType == OAuthClientType.MOBILE
                    ? linkMobileRedirectUriTemplate
                    : linkRedirectUriTemplate;
        };
        String resolved = template.replace("{baseUrl}", resolveBaseUrl());
        if (resolved.contains("{baseUrl}")) {
            throw new IllegalStateException(
                    "Notion redirect URI가 치환되지 않았습니다. app.oauth.base-url 및 yml 템플릿을 확인하세요: " + template);
        }
        return resolved;
    }

    /** Notion 로그인용 인증 URL (redirect_uri = resolveRedirectUri(LOGIN)) */
    public String getNotionAuthorizationUrl() {
        return getAuthorizationUrl(NotionOAuthFlow.LOGIN); //Notion 로그인용 인증 URL (redirect_uri = resolveRedirectUri(LOGIN))
    }

    /** 기존 계정 Notion 연동용 인증 URL (redirect_uri = resolveRedirectUri(LINK)) */
    public String getNotionLinkAuthorizationUrl() {
        return getAuthorizationUrl(NotionOAuthFlow.LINK); //기존 계정 Notion 연동용 인증 URL (redirect_uri = resolveRedirectUri(LINK))
    }

    public String getAuthorizationUrl(NotionOAuthFlow flow) {
        return getAuthorizationUrl(flow, OAuthClientType.WEB);
    }

    public String getAuthorizationUrl(NotionOAuthFlow flow, OAuthClientType clientType) {
        String redirectUri = resolveRedirectUri(flow, clientType);
        log.debug("Notion authorization URL flow={} client={} redirect_uri={}", flow, clientType, redirectUri);
        return buildAuthorizationUrl(redirectUri);
    }

    public String resolveLoginRedirectUri() {
        return resolveRedirectUri(NotionOAuthFlow.LOGIN); //Notion 로그인용 인증 URL (redirect_uri = resolveRedirectUri(LOGIN))
    }

    public String resolveLinkRedirectUri() {
        return resolveRedirectUri(NotionOAuthFlow.LINK); //기존 계정 Notion 연동용 인증 URL (redirect_uri = resolveRedirectUri(LINK))
    }

    public String buildLoginMobileCallbackBridgeHtml(String code, String error) {
        return OAuthCallbackBridgeHtml.build(notionLoginMobileDeepLink, code, error);
    }

    public String buildLinkMobileCallbackBridgeHtml(String code, String error) {
        return OAuthCallbackBridgeHtml.build(linkDeepLink, code, error);
    }

    /**
     * Notion 연동 웹(Swagger) 콜백 응답 — code를 JSON으로 반환.
     * 연동 완료는 POST /api/oauth2/notion/link (JWT + client=web) 로 수행.
     */
    public Map<String, String> buildLinkWebCallbackResponse(String code) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("client", OAuthClientType.WEB.name().toLowerCase());
        body.put("message", "POST /api/oauth2/notion/link with Authorization Bearer JWT and body { \"code\": \"<code>\", \"client\": \"web\" }");
        return body;
    }

    // 인가 코드(code)를 Notion 액세스 토큰으로 교환 (로그인 플로우 — LOGIN redirect_uri)
    public String exchangeCodeForToken(String code) {
        return exchangeCodeForToken(code, NotionOAuthFlow.LOGIN); //인가 코드(code)를 Notion 액세스 토큰으로 교환 (로그인 플로우 — LOGIN redirect_uri)
    }

    public String exchangeCodeForToken(String code, NotionOAuthFlow flow) {
        return exchangeCodeForToken(code, flow, OAuthClientType.WEB);
    }

    public String exchangeCodeForToken(String code, NotionOAuthFlow flow, OAuthClientType clientType) {
        String redirectUri = resolveRedirectUri(flow, clientType);
        log.debug("Notion token exchange flow={} client={} redirect_uri={}", flow, clientType, redirectUri);
        return exchangeCodeForTokenInternal(code, redirectUri);
    }

    private String exchangeCodeForTokenInternal(String code, String redirectUri) {
        return executeWithRetry(() -> { //인가 코드(code)를 Notion 액세스 토큰으로 교환 (로그인 플로우 — LOGIN redirect_uri)
            try {
                HttpHeaders headers = new HttpHeaders(); //HTTP 헤더 설정
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBasicAuth(clientId, clientSecret); //클라이언트 ID와 시크릿 설정

                Map<String, Object> body = Map.of( //요청 본문 설정
                        "grant_type", "authorization_code",
                        "code", code,
                        "redirect_uri", redirectUri //리다이렉트 URI 설정
                );

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers); //요청 엔티티 생성
                ResponseEntity<Map> response = restTemplate.exchange( //Notion 토큰 발급 엔드포인트로 요청
                        tokenUri, //Notion 토큰 발급 엔드포인트
                        HttpMethod.POST,
                        request, //요청 엔티티
                        Map.class //응답 본문 타입
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Object token = response.getBody().get("access_token"); //엑세스 토큰 추출
                    if (token instanceof String s && !s.isBlank()) {
                        return s; //엑세스 토큰 반환
                    }
                }

                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED); //엑세스 토큰 발급 실패 시 예외 발생
            } catch (BusinessException e) {
                throw e; //예외 발생 시 예외 전파
            } catch (Exception e) {
                log.error("Notion token exchange error (redirect_uri={}): {}", redirectUri, e.getMessage()); //로그 기록
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED, e); //엑세스 토큰 발급 실패 시 예외 발생
            }
        }); //인가 코드(code)를 Notion 액세스 토큰으로 교환 (로그인 플로우 — LOGIN redirect_uri)
    }

    // Notion 액세스 토큰으로 현재 사용자(me) 정보 조회
    public OAuthUserInfo getUserInfo(String accessToken) {
        return executeWithRetry(() -> { //Notion 액세스 토큰으로 현재 사용자(me) 정보 조회
            try {
                HttpHeaders headers = new HttpHeaders(); //HTTP 헤더 설정
                headers.setBearerAuth(accessToken); //엑세스 토큰 설정
                headers.set("Notion-Version", notionVersion); //Notion API 버전 헤더 설정

                HttpEntity<Void> request = new HttpEntity<>(headers); //요청 엔티티 생성
                ResponseEntity<Map> response = restTemplate.exchange( //Notion 사용자 정보 조회 엔드포인트로 요청
                        userInfoUri, //Notion 사용자 정보 조회 엔드포인트
                        HttpMethod.GET,
                        request, //요청 엔티티
                        Map.class //응답 본문 타입
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    return parseOAuthUserInfo(response.getBody()); //Notion 사용자 정보 파싱
                }

                throw new BusinessException(ErrorCode.OAUTH_USER_INFO_FAILED); //Notion 사용자 정보 조회 실패 시 예외 발생
            } catch (BusinessException e) {
                throw e; //예외 발생 시 예외 전파
            } catch (Exception e) {
                log.error("Notion user info error: {}", e.getMessage()); //로그 기록
                throw new BusinessException(ErrorCode.OAUTH_USER_INFO_FAILED, e); //Notion 사용자 정보 조회 실패 시 예외 발생
            }
        });
    }

    // 우리 서비스의 User와 Notion 계정을 연동하거나 갱신
    public void linkNotionAccount(User user,
                                  OAuthUserInfo userInfo,
                                  String accessToken,
                                  UserNotionAccountRepository repository) {
        repository.findByUser(user)
                .ifPresentOrElse(
                        existing -> { //기존 Notion 계정 정보 갱신
                            existing.setAccessToken(accessToken); //엑세스 토큰 설정
                            existing.setNotionUserId(userInfo.getProviderId());
                            existing.setNotionName(userInfo.getName()); //Notion 이름 설정
                            existing.setLinkedAt(java.time.LocalDateTime.now()); //연동 일시 설정
                            repository.save(existing); //Notion 계정 정보 저장
                        },
                        () -> { //기존 Notion 계정 정보가 없으면 새로 생성
                            UserNotionAccount account = new UserNotionAccount();
                            account.setUser(user); //사용자 설정
                            account.setNotionUserId(userInfo.getProviderId()); //Notion 사용자 ID 설정
                            account.setNotionName(userInfo.getName()); //Notion 이름 설정
                            account.setAccessToken(accessToken); //엑세스 토큰 설정
                            repository.save(account); //Notion 계정 정보 저장
                        }
                );
    }

    private String buildAuthorizationUrl(String redirectUri) {
        String encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        return UriComponentsBuilder.fromHttpUrl(authorizationUri) //Notion 인증 URL 생성
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", encodedRedirectUri) //리다이렉트 URI 설정
                .queryParam("response_type", "code") //응답 타입 설정
                .queryParam("owner", "user") //소유자 설정
                .build(true)
                .toUriString(); //Notion 인증 URL 생성
    }

    private String resolveBaseUrl() {
        String base = oauthBaseUrl == null ? "" : oauthBaseUrl.trim(); //OAuth 기본 URL 설정
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1); //OAuth 기본 URL 설정
        }
        return base.isEmpty() ? "http://localhost:8080" : base; //OAuth 기본 URL 설정
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> operation) {
        try { //연산 수행
            return operation.get(); //연산 결과 반환
        } catch (ResourceAccessException e) {
            log.warn("Network error occurred, retrying once: {}", e.getMessage()); //네트워크 오류 발생 시 로그 기록
            return operation.get(); //연산 결과 반환
        }
    }

    /**
     * OAuth 토큰의 /users/me 응답 파싱.
     * OAuth 토큰은 봇(bot) 사용자를 가리키므로 최상위 name은 연동 앱 이름이다.
     * 실제 로그인한 사람의 이름·이메일은 bot.owner.user 에 있다.
     */
    private OAuthUserInfo parseOAuthUserInfo(Map<String, Object> body) {
        String id = asString(body.get("id")); //사용자 ID 추출
        String name = asString(body.get("name")); //사용자 이름 추출
        String avatarUrl = asString(body.get("avatar_url")); //사용자 아바타 URL 추출
        String email = null; //이메일 초기화

        String type = asString(body.get("type")); //사용자 타입 추출
        if ("person".equals(type)) {
            Object personObj = body.get("person"); //사용자 정보 추출
            if (personObj instanceof Map<?, ?> person) {
                email = asString(person.get("email")); //이메일 추출
            }
        } else if ("bot".equals(type)) { //봇 사용자 정보 추출
            Object botObj = body.get("bot"); //봇 사용자 정보 추출
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

        return OAuthUserInfo.builder() //OAuthUserInfo 빌더 생성
                .provider("notion") //제공자 설정
                .providerId(id) //제공자 ID 설정
                .email(email) //이메일 설정
                .name(name != null && !name.isBlank() ? name : "Notion User") //이름 설정
                .picture(avatarUrl) //아바타 URL 설정
                .build(); //OAuthUserInfo 빌더 생성
    }

    private String asString(Object value) { //Object를 String으로 변환
        return value == null ? null : String.valueOf(value); //Object를 String으로 변환
    }
}
