package com.capston.demo.domain.user.service;

import com.capston.demo.domain.user.oauth.OAuthClientType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuth2ServiceTest {

    @Test
    void mobileAuthorizationUrlUsesMobileRedirectUri() {
        GoogleOAuth2Service service = newService();

        String url = service.getGoogleAuthorizationUrl(OAuthClientType.MOBILE);

        assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Foauth2%2Fgoogle%2Fcallback%2Fmobile");
    }

    @Test
    void mobileCallbackBridgeHtmlRedirectsToDeepLink() {
        GoogleOAuth2Service service = newService();

        String html = service.buildMobileCallbackBridgeHtml("code-1", null);

        assertThat(html).contains("meetflow://oauth/google?code=code-1");
    }

    private GoogleOAuth2Service newService() {
        GoogleOAuth2Service service = new GoogleOAuth2Service(new RestTemplate());
        ReflectionTestUtils.setField(service, "clientId", "test-client");
        ReflectionTestUtils.setField(service, "clientSecret", "test-secret");
        ReflectionTestUtils.setField(service, "webRedirectUriTemplate", "{baseUrl}/api/oauth2/google/callback");
        ReflectionTestUtils.setField(service, "mobileRedirectUriTemplate", "{baseUrl}/api/oauth2/google/callback/mobile");
        ReflectionTestUtils.setField(service, "authorizationUri", "https://accounts.google.com/o/oauth2/v2/auth");
        ReflectionTestUtils.setField(service, "tokenUri", "https://oauth2.googleapis.com/token");
        ReflectionTestUtils.setField(service, "userInfoUri", "https://openidconnect.googleapis.com/v1/userinfo");
        ReflectionTestUtils.setField(service, "oauthBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(service, "mobileDeepLink", "meetflow://oauth/google");
        return service;
    }
}
