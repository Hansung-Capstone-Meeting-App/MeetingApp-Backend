package com.capston.demo.domain.user.service;

import com.capston.demo.domain.user.oauth.NotionOAuthFlow;
import com.capston.demo.domain.user.oauth.OAuthClientType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class NotionOAuth2ServiceTest {

    @Test
    void mobileAuthorizationUrlUsesMobileRedirectUri() {
        NotionOAuth2Service service = newService();

        String url = service.getAuthorizationUrl(NotionOAuthFlow.LOGIN, OAuthClientType.MOBILE);

        assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Foauth2%2Fnotion%2Fcallback%2Fmobile");
    }

    @Test
    void linkMobileCallbackBridgeHtmlRedirectsToDeepLink() {
        NotionOAuth2Service service = newService();

        String html = service.buildLinkMobileCallbackBridgeHtml("abc123", null);

        assertThat(html).contains("meetflow://notion/link?code=abc123");
    }

    @Test
    void authorizationUrlUsesOnlyNotionOAuthParameters() {
        NotionOAuth2Service service = newService();

        String url = service.getAuthorizationUrl(NotionOAuthFlow.LINK);

        assertThat(url).startsWith("https://api.notion.com/v1/oauth/authorize?");
        assertThat(url).contains("client_id=test-client");
        assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Foauth2%2Fnotion%2Flink%2Fcallback");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("owner=user");
        assertThat(url).doesNotContain("scope=");
    }

    @Test
    void linkWebCallbackResponseReturnsCodeForSwagger() {
        NotionOAuth2Service service = newService();

        var body = service.buildLinkWebCallbackResponse("abc123");

        assertThat(body.get("code")).isEqualTo("abc123");
        assertThat(body.get("client")).isEqualTo("web");
        assertThat(body.get("message")).contains("POST /api/oauth2/notion/link");
    }

    private NotionOAuth2Service newService() {
        NotionOAuth2Service service = new NotionOAuth2Service(new RestTemplate());

        ReflectionTestUtils.setField(service, "clientId", "test-client");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");
        ReflectionTestUtils.setField(service, "loginRedirectUriTemplate", "{baseUrl}/api/oauth2/notion/callback");
        ReflectionTestUtils.setField(service, "loginMobileRedirectUriTemplate", "{baseUrl}/api/oauth2/notion/callback/mobile");
        ReflectionTestUtils.setField(service, "linkRedirectUriTemplate", "{baseUrl}/api/oauth2/notion/link/callback");
        ReflectionTestUtils.setField(service, "linkMobileRedirectUriTemplate", "{baseUrl}/api/oauth2/notion/link/callback/mobile");
        ReflectionTestUtils.setField(service, "authorizationUri", "https://api.notion.com/v1/oauth/authorize");
        ReflectionTestUtils.setField(service, "tokenUri", "https://api.notion.com/v1/oauth/token");
        ReflectionTestUtils.setField(service, "userInfoUri", "https://api.notion.com/v1/users/me");
        ReflectionTestUtils.setField(service, "notionVersion", "2022-06-28");
        ReflectionTestUtils.setField(service, "oauthBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(service, "notionLoginMobileDeepLink", "meetflow://oauth/notion");
        ReflectionTestUtils.setField(service, "linkDeepLink", "meetflow://notion/link");

        return service;
    }
}
