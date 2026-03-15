package com.capston.demo.domain.calender.controller;

import com.capston.demo.domain.calender.controllerDocs.EventControllerDocs;
import com.capston.demo.domain.calender.dto.response.EventResponse;
import com.capston.demo.domain.calender.service.EventService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController implements EventControllerDocs {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventResponse>> getEvents(
            @RequestParam(required = false) Long meetingId,
            @RequestParam(required = false) Long workspaceId
    ) {
        return ResponseEntity.ok(eventService.getEvents(meetingId, workspaceId));
    }
}
