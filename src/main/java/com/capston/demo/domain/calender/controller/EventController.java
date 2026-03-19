package com.capston.demo.domain.calender.controller;

import com.capston.demo.domain.calender.controllerDocs.EventControllerDocs;
import com.capston.demo.domain.calender.dto.response.EventResponse;
import com.capston.demo.domain.calender.service.EventService;
import com.capston.demo.global.security.CustomUserDetails;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long meetingId,
            @RequestParam(required = false) Long workspaceId
    ) {
        Long userId = userDetails.getUserId();
        return ResponseEntity.ok(eventService.getEvents(meetingId, workspaceId, userId));
    }
}
