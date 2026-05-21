package com.capston.demo.domain.user.dto.response;

import lombok.Getter;

@Getter
public class NotionCalendarTargetResponse {

    private final String id;
    private final String name;
    private final String type;
    private final String url;

    public NotionCalendarTargetResponse(String id, String name, String type, String url) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.url = url;
    }
}
