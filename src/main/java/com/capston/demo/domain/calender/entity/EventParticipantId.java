package com.capston.demo.domain.calender.entity;

import lombok.EqualsAndHashCode;

import java.io.Serializable;

// 복합키 객체
@EqualsAndHashCode
public class EventParticipantId implements Serializable {
    private Long event;
    private Long userId;
}
