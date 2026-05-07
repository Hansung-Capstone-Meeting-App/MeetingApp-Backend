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

    // Notion OAuth 클라이언트 ID
    @Value("${spring.security.oauth2.client.registration.notion.client-id}")
    private String clientId;

    // Notion OAuth 클라이언트 시크릿
    @Value("${spring.security.oauth2.client.registration.notion.client-secret}")
    private String clientSecret;

    // Notion 리다이렉트 URI 템플릿 (예: {baseUrl}/api/oauth2/notion/callback)
    @Value("${spring.security.oauth2.client.registration.notion.redirect-uri}")
    private String redirectUri;

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

    public String getNotionAuthorizationUrl() {
        // application.yml의 {baseUrl} 플레이스홀더를 실제 서버 주소로 치환
        String finalRedirectUri = redirectUri.replace("{baseUrl}", "http://localhost:8080"); //실제 서버 주소로 변경
        return UriComponentsBuilder.fromHttpUrl(authorizationUri) //Notion 인증 페이지 URL
                .queryParam("client_id", clientId) //Notion OAuth 클라이언트 ID
                .queryParam("redirect_uri", finalRedirectUri) //Notion 리다이렉트 URI
                .queryParam("response_type", "code") //인증 코드 발급 요청
                .queryParam("owner", "user") //사용자 인증 요청
                .queryParam("scope", scope) //요청할 Notion 권한 범위
                .build() //URL 빌더 생성(최종 URL 생성)
                .toUriString(); //URL 문자열로 변환
    }

    // 인가 코드(code)를 Notion 액세스 토큰으로 교환
    public String exchangeCodeForToken(String code) {
        return executeWithRetry(() -> {
            try {
                HttpHeaders headers = new HttpHeaders(); //HTTP 헤더 생성
                headers.setContentType(MediaType.APPLICATION_JSON); //Content-Type을 JSON으로 설정
                // Notion은 client_id/client_secret을 Basic Auth로 요구
                headers.setBasicAuth(clientId, clientSecret); //Basic Auth 설정

                Map<String, Object> body = Map.of( //요청 바디 생성
                        "grant_type", "authorization_code",
                        "code", code,
                        "redirect_uri", redirectUri.replace("{baseUrl}", "http://localhost:8080")
                );

                // JSON 바디로 토큰 엔드포인트 호출
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers); //HTTP 요청 엔티티 생성
                ResponseEntity<Map> response = restTemplate.exchange( //Notion 토큰 엔드포인트 호출
                        tokenUri,
                        HttpMethod.POST,
                        request,
                        Map.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) { //응답 상태 코드가 200 OK이고 바디가 존재하면
                    Object token = response.getBody().get("access_token"); //엑세스 토큰 추출
                    if (token instanceof String s && !s.isBlank()) { //엑세스 토큰이 비어있지 않으면
                        return s; //엑세스 토큰 반환
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
        return executeWithRetry(() -> { //엑세스 토큰을 사용하여 사용자 정보 조회
            try {
                HttpHeaders headers = new HttpHeaders(); //HTTP 헤더 생성
                headers.setBearerAuth(accessToken); //Bearer Auth 설정
                // Notion API 버전은 헤더로 전달해야 함
                headers.set("Notion-Version", notionVersion); //Notion API 버전 헤더 설정

                HttpEntity<Void> request = new HttpEntity<>(headers); //HTTP 요청 엔티티 생성
                ResponseEntity<Map> response = restTemplate.exchange( //Notion 사용자 정보 조회 엔드포인트 호출
                        userInfoUri,
                        HttpMethod.GET,
                        request,
                        Map.class
                );

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) { //응답 상태 코드가 200 OK이고 바디가 존재하면
                    Map<String, Object> body = response.getBody(); //바디 추출
                    String id = asString(body.get("id")); //사용자 ID 추출
                    String name = asString(body.get("name")); //사용자 이름 추출
                    String avatarUrl = asString(body.get("avatar_url")); //사용자 아바타 URL 추출(사진)

                    String email = null; //이메일 초기화(Notion 사용자 정보에 이메일이 없을 수 있음)
                    // 이메일은 person.email 안에 있을 수 있음 (없을 수도 있음)
                    Object personObj = body.get("person"); //person 객체 추출
                    if (personObj instanceof Map<?, ?> person) { //person 객체가 Map인 경우
                        email = asString(person.get("email")); //이메일 추출
                    }

                    return OAuthUserInfo.builder() //OAuthUserInfo 빌더 생성
                            .provider("notion") //제공자 설정
                            .providerId(id) //제공자 ID 설정
                            .email(email) //이메일 설정
                            .name(name != null && !name.isBlank() ? name : "Notion User") //이름 설정(이름이 없을 수 있음)
                            .picture(avatarUrl) //사진 설정
                            .build(); //OAuthUserInfo 객체 생성
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

    // 우리 서비스의 User와 Notion 계정을 연동하거나 갱신
    public void linkNotionAccount(User user,
                                  OAuthUserInfo userInfo,
                                  String accessToken,
                                  UserNotionAccountRepository repository) {
        repository.findByUser(user)
                .ifPresentOrElse( //Notion 계정이 존재하면 업데이트, 없으면 생성 (이미 로그인한 사용자가 노션 계정을 연결)
                        existing -> {
                            existing.setAccessToken(accessToken); //엑세스 토큰 설정
                            existing.setNotionUserId(userInfo.getProviderId()); //Notion 사용자 ID 설정
                            existing.setNotionName(userInfo.getName()); //Notion 사용자 이름 설정
                            existing.setLinkedAt(java.time.LocalDateTime.now()); //연결 시간 설정
                            repository.save(existing); //Notion 계정 저장
                        },
                        () -> { //Notion 계정이 존재하지 않으면 생성 (최초 로그인)
                            UserNotionAccount account = new UserNotionAccount();
                            account.setUser(user);
                            account.setNotionUserId(userInfo.getProviderId());
                            account.setNotionName(userInfo.getName());
                            account.setAccessToken(accessToken);
                            repository.save(account);
                        }
                );
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ResourceAccessException e) {
            log.warn("Network error occurred, retrying once: {}", e.getMessage());
            return operation.get();
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

