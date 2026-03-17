package com.capston.demo.domain.user.service;

import com.capston.demo.domain.user.dto.OAuthUserInfo;
import com.capston.demo.global.exception.OAuthAuthenticationException;
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

    // Google OAuth 클라이언트 ID
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    // Google OAuth 클라이언트 시크릿
    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    // Google 리다이렉트 URI 템플릿 (예: {baseUrl}/api/oauth2/google/callback)
    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    // Google 인증 페이지 URL
    @Value("${spring.security.oauth2.client.provider.google.authorization-uri}")
    private String authorizationUri;

    // Google 토큰 발급 엔드포인트
    @Value("${spring.security.oauth2.client.provider.google.token-uri}")
    private String tokenUri;

    // Google 사용자 정보 조회 엔드포인트
    @Value("${spring.security.oauth2.client.provider.google.user-info-uri}")
    private String userInfoUri;

    // OpenID Connect 표준 스코프
    private final String scope = "openid profile email";

    public String getGoogleAuthorizationUrl() {
        // application.yml의 {baseUrl} 플레이스홀더를 실제 서버 주소로 치환
        String finalRedirectUri = redirectUri.replace("{baseUrl}", "http://localhost:8080");
        log.info("=== OAuth Debug ===");
        log.info("Redirect URI from config: {}", redirectUri);
        log.info("Final Redirect URI: {}", finalRedirectUri);

        String authUrl = UriComponentsBuilder.fromHttpUrl(authorizationUri)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", finalRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .build()
                .toUriString();

        log.info("Full Auth URL: {}", authUrl);
        log.info("==================");

        return authUrl;
    }

    // 인가 코드(code)를 Google 액세스 토큰으로 교환
    public String exchangeCodeForToken(String code) {
        return executeWithRetry(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                String body = UriComponentsBuilder.newInstance()
                        .queryParam("code", code)
                        .queryParam("client_id", clientId)
                        .queryParam("client_secret", clientSecret)
                        .queryParam("redirect_uri", redirectUri.replace("{baseUrl}", "http://localhost:8080"))
                        .queryParam("grant_type", "authorization_code")
                        .build()
                        .getQuery();

                // x-www-form-urlencoded 형식으로 토큰 엔드포인트 호출
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

                throw new OAuthAuthenticationException(
                        "Failed to exchange code for token",
                        "google",
                        "TOKEN_EXCHANGE_FAILED"
                );
            } catch (OAuthAuthenticationException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error exchanging code for token: {}", e.getMessage());
                throw new OAuthAuthenticationException(
                        "Failed to exchange code for token: " + e.getMessage(),
                        "google",
                        "TOKEN_EXCHANGE_ERROR",
                        e
                );
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

                throw new OAuthAuthenticationException(
                        "Failed to get user info",
                        "google",
                        "USER_INFO_FAILED"
                );
            } catch (OAuthAuthenticationException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error getting user info: {}", e.getMessage());
                throw new OAuthAuthenticationException(
                        "Failed to get user info: " + e.getMessage(),
                        "google",
                        "USER_INFO_ERROR",
                        e
                );
            }
        });
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

