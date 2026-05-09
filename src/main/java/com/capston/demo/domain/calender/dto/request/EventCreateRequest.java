package com.capston.demo.domain.calender.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class EventCreateRequest {

    @NotBlank
    private String title;

    private String description;

    private String location;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startAt;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endAt;

    private Boolean isAllDay = false;

    private Long workspaceId;

    private Long meetingId;

    private String color;

    private List<Long> participantUserIds;
}
