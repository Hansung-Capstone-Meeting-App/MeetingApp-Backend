package com.capston.demo.domain.calender.repository;
import com.capston.demo.domain.calender.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByMeetingId(Long meetingId);
    List<Event> findByWorkspaceId(Long workspaceId);
    List<Event> findByWorkspaceIdIn(List<Long> workspaceIds);
    List<Event> findByWorkspaceIdAndCreatedBy(Long workspaceId, Long createdBy);
    long countByMeetingId(Long meetingId);
    void deleteByMeetingId(Long meetingId);
    void deleteByWorkspaceId(Long workspaceId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Event e SET e.notionPageId = null, e.notionSyncedAt = null WHERE e.workspaceId IN :workspaceIds")
    int clearNotionLinksByWorkspaceIds(@Param("workspaceIds") List<Long> workspaceIds);
}
