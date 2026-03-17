package com.capston.demo.domain.user.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 캘린더로 사용할 노션 데이터베이스를 등록할 때 사용.
 * databaseUrl 또는 databaseId 중 하나만 넣으면 됨.
 */
@Getter
@Setter
public class SetCalendarDatabaseRequestDto {

    /** 노션 데이터베이스 페이지 URL (예: https://www.notion.so/.../3230e99b88308071b256df5dfcc8bfe7?v=...) */
    private String databaseUrl;

    /** 또는 데이터베이스 ID만 직접 전달 (32자 hex, 하이픈 있음/없음 모두 가능) */
    private String databaseId;
}
