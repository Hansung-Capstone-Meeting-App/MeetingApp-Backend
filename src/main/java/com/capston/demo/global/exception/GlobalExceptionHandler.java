package com.capston.demo.global.exception;


import com.capston.demo.domain.user.dto.response.ErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 검증 실패 처리 (400 Bad Request)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                "입력값 검증에 실패했습니다",
                errors,
                LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // 중복 이메일 처리 (409 Conflict)
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponseDto> handleDuplicateEmailException(DuplicateEmailException ex) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.CONFLICT.value(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    // 유효하지 않은 토큰 처리 (401 Unauthorized)
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidTokenException(InvalidTokenException ex) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    // 만료된 토큰 처리 (401 Unauthorized)
    @ExceptionHandler(ExpiredTokenException.class)
    public ResponseEntity<ErrorResponseDto> handleExpiredTokenException(ExpiredTokenException ex) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    // OAuth 인증 실패 처리 (400 Bad Request 또는 500 Internal Server Error)
    @ExceptionHandler(OAuthAuthenticationException.class)
    public ResponseEntity<ErrorResponseDto> handleOAuthAuthenticationException(OAuthAuthenticationException ex) {
        // 사용자 친화적인 오류 메시지 생성
        String userFriendlyMessage;
        HttpStatus status;

        switch (ex.getErrorCode()) {
            case "TOKEN_EXCHANGE_FAILED":
            case "TOKEN_EXCHANGE_ERROR":
                userFriendlyMessage = "OAuth 로그인 중 오류가 발생했습니다. 다시 시도해주세요.";
                status = HttpStatus.BAD_REQUEST;
                break;
            case "USER_INFO_FAILED":
            case "USER_INFO_ERROR":
                userFriendlyMessage = "사용자 정보를 가져오는 중 오류가 발생했습니다. 다시 시도해주세요.";
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
            case "INVALID_USER_INFO":
                userFriendlyMessage = "OAuth 계정에서 사용자 정보를 충분히 가져올 수 없습니다.";
                status = HttpStatus.BAD_REQUEST;
                break;
            case "NOTION_EVENT_CREATE_FAILED":
            case "NOTION_EVENT_CREATE_ERROR":
                userFriendlyMessage = "노션 캘린더에 일정을 생성하지 못했습니다. 노션에서 해당 데이터베이스에 연동 앱을 추가했는지, 데이터베이스 ID와 속성 이름이 맞는지 확인해주세요.";
                status = HttpStatus.BAD_REQUEST;
                break;
            default:
                userFriendlyMessage = "OAuth 인증 중 오류가 발생했습니다.";
                status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        ErrorResponseDto errorResponse;
        if ("NOTION_EVENT_CREATE_FAILED".equals(ex.getErrorCode()) || "NOTION_EVENT_CREATE_ERROR".equals(ex.getErrorCode())) {
            Map<String, String> detail = new HashMap<>();
            detail.put("notionError", ex.getMessage() != null ? ex.getMessage() : "");
            errorResponse = new ErrorResponseDto(status.value(), userFriendlyMessage, detail, LocalDateTime.now());
        } else {
            errorResponse = new ErrorResponseDto(status.value(), userFriendlyMessage);
        }

        return ResponseEntity.status(status).body(errorResponse);
    }

    // 인증 실패 처리 (401 Unauthorized)
    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleBadCredentialsException(
            org.springframework.security.authentication.BadCredentialsException ex) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    // 기타 예외 처리 (500 Internal Server Error)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneralException(Exception ex) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "서버 오류가 발생했습니다"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
