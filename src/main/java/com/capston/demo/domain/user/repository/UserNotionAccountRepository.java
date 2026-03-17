package com.capston.demo.domain.user.repository;

import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.entity.UserNotionAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserNotionAccountRepository extends JpaRepository<UserNotionAccount, Long> {

    Optional<UserNotionAccount> findByUser(User user);

    Optional<UserNotionAccount> findByUser_Id(Long userId);
}

