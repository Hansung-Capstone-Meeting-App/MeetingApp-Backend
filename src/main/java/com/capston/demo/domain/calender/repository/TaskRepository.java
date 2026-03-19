package com.capston.demo.domain.calender.repository;
import com.capston.demo.domain.calender.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByMeetingIdAndCreatedBy(Long meetingId, Long createdBy);
    List<Task> findByWorkspaceIdAndCreatedBy(Long workspaceId, Long createdBy);
    List<Task> findByAssigneeId(Long assigneeId);
}
