package com.capston.demo.domain.user.oauth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthCallbackBridgeHtmlTest {

    @Test
    void buildDeepLinkWithCode() {
        assertThat(OAuthCallbackBridgeHtml.buildDeepLink("meetflow://oauth/google", "abc", null))
                .isEqualTo("meetflow://oauth/google?code=abc");
    }

    @Test
    void buildHtmlRedirectsToDeepLink() {
        String html = OAuthCallbackBridgeHtml.build("meetflow://oauth/notion", "xyz", null);

        assertThat(html).contains("window.location.replace(\"meetflow://oauth/notion?code=xyz\")");
    }
}
