package com.capston.demo.global.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // User
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다"),
    DUPLICATE_EMAIL(409, "이미 사용 중인 이메일입니다"),
    INVALID_PASSWORD(401, "현재 비밀번호가 올바르지 않습니다"),

    // Auth
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다"),
    EXPIRED_TOKEN(401, "만료된 리프레시 토큰입니다"),

    // Workspace
    WORKSPACE_NOT_FOUND(404, "워크스페이스를 찾을 수 없습니다"),
    NOT_WORKSPACE_MEMBER(403, "워크스페이스 멤버가 아닙니다"),
    ALREADY_MEMBER(409, "이미 멤버입니다"),
    WORKSPACE_ID_REQUIRED(400, "workspaceId는 필수입니다"),

    // Meeting
    MEETING_NOT_FOUND(404, "회의를 찾을 수 없습니다"),
    MEETING_ACCESS_DENIED(403, "접근 권한이 없습니다"),

    // Recording
    RECORDING_NOT_FOUND(404, "녹음 파일을 찾을 수 없습니다"),
    RECORDING_ACCESS_DENIED(403, "접근 권한이 없습니다"),

    // Transcript
    TRANSCRIPT_NOT_FOUND(404, "트랜스크립트를 찾을 수 없습니다"),

    // OAuth
    OAUTH_TOKEN_EXCHANGE_FAILED(400, "OAuth 로그인 중 오류가 발생했습니다. 다시 시도해주세요."),
    OAUTH_USER_INFO_FAILED(500, "사용자 정보를 가져오는 중 오류가 발생했습니다. 다시 시도해주세요."),
    OAUTH_INVALID_USER_INFO(400, "OAuth 계정에서 사용자 정보를 충분히 가져올 수 없습니다."),

    // Notion
    NOTION_EVENT_CREATE_FAILED(400, "노션 캘린더에 일정을 생성하지 못했습니다. 데이터베이스 ID와 권한을 확인해주세요.");

    private final int status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
