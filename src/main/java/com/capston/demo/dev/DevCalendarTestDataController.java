package com.capston.demo.dev;

import com.capston.demo.domain.calender.entity.Event;
import com.capston.demo.domain.calender.repository.EventRepository;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.entity.Workspace;
import com.capston.demo.domain.user.entity.WorkspaceMember;
import com.capston.demo.domain.user.repository.UserRepository;
import com.capston.demo.domain.user.repository.WorkspaceMemberRepository;
import com.capston.demo.domain.user.repository.WorkspaceRepository;
import com.capston.demo.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 개발/테스트 전용 API. spring.profiles.active=dev 일 때만 로드됨.
 * Events, Workspace, WorkspaceMember가 비어 있어도 노션 캘린더 동기화를 Swagger에서 테스트할 수 있도록
 * 더미 데이터(워크스페이스 1개, 현재 유저를 멤버로 등록, 이벤트 1개)를 생성한다.
 */
@Profile("dev")
@RestController
@RequestMapping("/api/dev/test-data")
@RequiredArgsConstructor
@Slf4j
public class DevCalendarTestDataController {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final EventRepository eventRepository;

    /**
     * 현재 로그인한 유저 기준으로 테스트용 워크스페이스 1개, 멤버 1명(본인), 이벤트 1개를 생성하고
     * eventId, workspaceId를 반환한다.
     * Swagger에서 이 API 호출 후 반환된 eventId로 POST /api/calendar/events/{eventId}/notion-sync 를 호출하면
     * 노션 캘린더 동기화를 검증할 수 있다.
     */
    @PostMapping("/calendar-setup")
    public ResponseEntity<?> setupCalendarTestData() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long userId = userDetails.getUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        String slug = "test-ws-" + System.currentTimeMillis();
        Workspace workspace = new Workspace("테스트 워크스페이스", slug, user);
        workspace = workspaceRepository.save(workspace);

        WorkspaceMember member = new WorkspaceMember(workspace, user);
        workspaceMemberRepository.save(member);

        Event event = new Event();
        event.setWorkspaceId(workspace.getId());
        event.setTitle("Swagger 테스트 일정");
        event.setDescription("노션 캘린더 동기화 테스트용");
        event.setStartAt(LocalDateTime.now().plusDays(1));
        event.setEndAt(LocalDateTime.now().plusDays(1).plusHours(1));
        event.setCreatedBy(userId);
        event = eventRepository.save(event);

        log.info("Dev test data created: workspaceId={}, eventId={}, userId={}", workspace.getId(), event.getId(), userId);

        return ResponseEntity.ok(Map.of(
                "eventId", event.getId(),
                "workspaceId", workspace.getId(),
                "message", "테스트용 워크스페이스·이벤트가 생성되었습니다. POST /api/calendar/events/" + event.getId() + "/notion-sync 로 노션 동기화를 테스트하세요."
        ));
    }
}
