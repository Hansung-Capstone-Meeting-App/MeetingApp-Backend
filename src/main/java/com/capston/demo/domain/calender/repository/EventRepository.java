package com.capston.demo.domain.calender.repository;
import com.capston.demo.domain.calender.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByMeetingId(Long meetingId);
    // 특정 워크스페이스에 속한 모든 이벤트 조회
    List<Event> findByWorkspaceId(Long workspaceId);
}
