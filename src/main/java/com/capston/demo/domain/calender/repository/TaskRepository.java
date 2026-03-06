package com.capston.demo.domain.calender.repository;
import com.capston.demo.domain.calender.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByMeetingId(Long meetingId);
    List<Task> findByAssigneeId(Long assigneeId);
    List<Task> findByWorkspaceId(Long workspaceId);
}
