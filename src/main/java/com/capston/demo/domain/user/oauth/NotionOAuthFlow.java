package com.capston.demo.domain.user.oauth;

/**
 * Notion OAuth 플로우 구분.
 * auth-url 의 redirect_uri 와 code 교환 시 redirect_uri 가 반드시 동일해야 함.
 */
public enum NotionOAuthFlow {
    /** Notion 로그인 — web: /notion/callback, mobile: /notion/callback/mobile */
    LOGIN,
    /** 기존 계정 Notion 연동 — web: /notion/link/callback, mobile: /notion/link/callback/mobile */
    LINK
}
