package com.capston.demo.domain.user.oauth;

/**
 * OAuth 클라이언트 플랫폼.
 * auth-url · redirect_uri · code 교환 시 동일한 값을 사용해야 한다.
 */
public enum OAuthClientType {
    WEB,
    MOBILE;

    public static OAuthClientType from(String value) {
        return from(value, WEB);
    }

    public static OAuthClientType from(String value, OAuthClientType defaultType) {
        if (value == null || value.isBlank()) {
            return defaultType;
        }
        return "mobile".equalsIgnoreCase(value.trim()) ? MOBILE : WEB;
    }
}
