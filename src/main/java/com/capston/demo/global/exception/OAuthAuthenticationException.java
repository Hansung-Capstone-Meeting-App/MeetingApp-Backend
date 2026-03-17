package com.capston.demo.global.exception;

import lombok.Getter;

@Getter
public class OAuthAuthenticationException extends RuntimeException {
    private final String provider;
    private final String errorCode;

    public OAuthAuthenticationException(String message, String provider, String errorCode) {
        super(message);
        this.provider = provider;
        this.errorCode = errorCode;
    }

    public OAuthAuthenticationException(String message, String provider, String errorCode, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.errorCode = errorCode;
    }
}
