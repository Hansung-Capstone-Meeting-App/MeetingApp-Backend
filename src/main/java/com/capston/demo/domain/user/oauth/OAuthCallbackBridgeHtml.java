package com.capston.demo.domain.user.oauth;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * 모바일 OAuth callback HTML — HTTPS redirect 수신 후 앱 딥링크로 code/error 전달.
 */
public final class OAuthCallbackBridgeHtml {

    private OAuthCallbackBridgeHtml() {
    }

    public static String build(String deepLinkBase, String code, String error) {
        String deepLink = buildDeepLink(deepLinkBase, code, error);
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <title>OAuth callback</title>
                </head>
                <body>
                <script>
                window.location.replace("%s");
                </script>
                </body>
                </html>
                """.formatted(escapeJs(deepLink));
    }

    public static String buildDeepLink(String deepLinkBase, String code, String error) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(deepLinkBase);
        if (code != null && !code.isBlank()) {
            builder.queryParam("code", code);
        } else {
            String err = (error != null && !error.isBlank()) ? error : "missing_code";
            builder.queryParam("error", err);
        }
        return builder.build(true).toUriString();
    }

    private static String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("</", "<\\/");
    }
}
