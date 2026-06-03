package com.capston.demo.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Notion에 캘린더용 database를 새로 만들 때 사용.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateNotionCalendarDatabaseRequestDto {

    /** DB 표시 이름 (미입력 시 기본값 "Meetflow 일정") */
    private String name;

    /**
     * DB를 만들 부모 Notion 페이지 ID.
     * 없으면 PUT /notion/root-page 로 저장된 rootPageId 를 사용한다.
     */
    private String parentPageId;
}
