package com.capston.demo.domain.user.service;

import com.capston.demo.domain.user.dto.OAuthUserInfo;
import com.capston.demo.domain.user.oauth.OAuthCallbackBridgeHtml;
import com.capston.demo.domain.user.oauth.OAuthClientType;
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

import java.util.Map;

// Google OAuth2 전용 토큰/유저 정보 처리를 담당하는 서비스
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuth2Service {

    private final RestTemplate restTemplate;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri:{baseUrl}/api/oauth2/google/callback}")
    private String webRedirectUriTemplate;

    @Value("${spring.security.oauth2.client.registration.google.mobile-redirect-uri:{baseUrl}/api/oauth2/google/callback/mobile}")
    private String mobileRedirectUriTemplate;

    @Value("${spring.security.oauth2.client.provider.google.authorization-uri}")
    private String authorizationUri;

    @Value("${spring.security.oauth2.client.provider.google.token-uri}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.provider.google.user-info-uri}")
    private String userInfoUri;

    @Value("${app.oauth.base-url:http://localhost:8080}")
    private String oauthBaseUrl;

    @Value("${app.oauth.mobile.google-deep-link:meetflow://oauth/google}")
    private String mobileDeepLink;

    private final String scope = "openid profile email";

    @PostConstruct
    void logResolvedRedirectUris() {
        log.info("Google OAuth WEB redirect_uri={}", resolveRedirectUri(OAuthClientType.WEB));
        log.info("Google OAuth MOBILE redirect_uri={}", resolveRedirectUri(OAuthClientType.MOBILE));
    }

    public String resolveRedirectUri(OAuthClientType clientType) {
        String template = clientType == OAuthClientType.MOBILE
                ? mobileRedirectUriTemplate
                : webRedirectUriTemplate;
        String resolved = template.replace("{baseUrl}", resolveBaseUrl());
        if (resolved.contains("{baseUrl}")) {
            throw new IllegalStateException(
                    "Google redirect URI가 치환되지 않았습니다. app.oauth.base-url 및 yml 템플릿을 확인하세요: " + template);
        }
        return resolved;
    }

    public String getGoogleAuthorizationUrl() {
        return getGoogleAuthorizationUrl(OAuthClientType.WEB);
    }

    public String getGoogleAuthorizationUrl(OAuthClientType clientType) {
        String redirectUri = resolveRedirectUri(clientType);
        log.debug("Google authorization URL client={} redirect_uri={}", clientType, redirectUri);
        return UriComponentsBuilder.fromHttpUrl(authorizationUri)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .build()
                .toUriString();
    }

    public String buildMobileCallbackBridgeHtml(String code, String error) {
        return OAuthCallbackBridgeHtml.build(mobileDeepLink, code, error);
    }

    public String exchangeCodeForToken(String code) {
        return exchangeCodeForToken(code, OAuthClientType.WEB);
    }

    public String exchangeCodeForToken(String code, OAuthClientType clientType) {
        String redirectUri = resolveRedirectUri(clientType);
        log.debug("Google token exchange client={} redirect_uri={}", clientType, redirectUri);
        return executeWithRetry(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                String body = UriComponentsBuilder.newInstance()
                        .queryParam("code", code)
                        .queryParam("client_id", clientId)
                        .queryParam("client_secret", clientSecret)
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("grant_type", "authorization_code")
                        .build()
                        .getQuery();

                HttpEntity<String> request = new HttpEntity<>(body, headers);
                ResponseEntity<Map> response = restTemplate.exchange(
                        tokenUri,
                        HttpMethod.POST,
                        request,
                        Map.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    return (String) response.getBody().get("access_token");
                }

                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error exchanging code for token (redirect_uri={}): {}", redirectUri, e.getMessage());
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED, e);
            }
        });
    }

    public OAuthUserInfo getUserInfo(String accessToken) {
        return executeWithRetry(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(accessToken);

                HttpEntity<String> request = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(
                        userInfoUri,
                        HttpMethod.GET,
                        request,
                        Map.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    Map<String, Object> userInfo = response.getBody();
                    return OAuthUserInfo.builder()
                            .providerId((String) userInfo.get("sub"))
                            .email((String) userInfo.get("email"))
                            .name((String) userInfo.get("name"))
                            .picture((String) userInfo.get("picture"))
                            .provider("google")
                            .build();
                }

                throw new BusinessException(ErrorCode.OAUTH_USER_INFO_FAILED);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error getting user info: {}", e.getMessage());
                throw new BusinessException(ErrorCode.OAUTH_USER_INFO_FAILED, e);
            }
        });
    }

    private String resolveBaseUrl() {
        String base = oauthBaseUrl == null ? "" : oauthBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.isEmpty() ? "http://localhost:8080" : base;
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ResourceAccessException e) {
            log.warn("Network error occurred, retrying once: {}", e.getMessage());
            return operation.get();
        }
    }
}
