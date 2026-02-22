package com.capston.demo.domain.calender.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "event_participants")
@IdClass(EventParticipantId.class)
@Getter
@Setter
public class EventParticipant {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Id
    private Long userId;

    @Enumerated(EnumType.STRING)
    private ParticipantStatus status = ParticipantStatus.PENDING;
}

