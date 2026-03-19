package com.capston.demo.domain.calender.repository;
import com.capston.demo.domain.calender.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByMeetingIdAndCreatedBy(Long meetingId, Long createdBy);
    List<Event> findByWorkspaceIdAndCreatedBy(Long workspaceId, Long createdBy);
}
