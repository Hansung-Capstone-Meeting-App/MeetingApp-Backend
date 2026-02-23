package com.capston.demo.domain.meeting.repository;

import com.capston.demo.domain.meeting.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId);

    List<Meeting> findByChannelIdOrderByCreatedAtDesc(Long channelId);

    List<Meeting> findByWorkspaceIdAndChannelIdOrderByCreatedAtDesc(Long workspaceId, Long channelId);
}
