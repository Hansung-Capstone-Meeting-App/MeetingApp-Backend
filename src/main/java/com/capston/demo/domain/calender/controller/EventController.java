package com.capston.demo.domain.calender.controller;

import com.capston.demo.domain.calender.controllerDocs.EventControllerDocs;
import com.capston.demo.domain.calender.dto.request.EventCreateRequest;
import com.capston.demo.domain.calender.dto.request.EventUpdateRequest;
import com.capston.demo.domain.calender.dto.response.EventResponse;
import com.capston.demo.domain.calender.service.EventService;
import com.capston.demo.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(eventService.getEvents(meetingId, workspaceId, userDetails.getUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(eventService.getEvent(id, userDetails.getUserId()));
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody EventCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.createEvent(request, userDetails.getUserId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody EventUpdateRequest request
    ) {
        return ResponseEntity.ok(eventService.updateEvent(id, request, userDetails.getUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id
    ) {
        eventService.deleteEvent(id, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }
}
