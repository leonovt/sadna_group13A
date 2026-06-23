package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserNotificationJpaRepository extends JpaRepository<UserNotificationEntity, String> {

    List<UserNotificationEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
