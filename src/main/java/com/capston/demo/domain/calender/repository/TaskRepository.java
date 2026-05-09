package com.capston.demo.domain.calender.repository;
import com.capston.demo.domain.calender.entity.Task;
import com.capston.demo.domain.calender.entity.TaskSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByMeetingId(Long meetingId);
    List<Task> findByWorkspaceId(Long workspaceId);
    List<Task> findByAssigneeId(Long assigneeId);
    void deleteByMeetingIdAndSource(Long meetingId, TaskSource source);
}
