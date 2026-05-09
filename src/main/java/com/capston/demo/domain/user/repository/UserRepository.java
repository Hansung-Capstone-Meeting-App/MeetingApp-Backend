package com.capston.demo.domain.user.repository;

import com.capston.demo.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findBySlackUserId(String slackUserId);

    @Query("SELECT u FROM User u WHERE u.status <> 'deleted' AND (u.name LIKE %:q% OR u.email LIKE %:q%)")
    List<User> searchByNameOrEmail(@Param("q") String q);
}
