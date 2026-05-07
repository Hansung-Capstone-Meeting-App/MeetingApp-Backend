package com.capston.demo.domain.meeting.repository;

import com.capston.demo.domain.meeting.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    Optional<Meeting> findByIdAndCreatedBy(Long id, Long createdBy);

    List<Meeting> findByCreatedByOrderByCreatedAtDesc(Long createdBy);

    List<Meeting> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);
}