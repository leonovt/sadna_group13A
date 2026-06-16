package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationJpaRepository extends JpaRepository<PendingNotificationEntity, String> {
    List<PendingNotificationEntity> findByUserId(String userId);
    void deleteByUserId(String userId);
}
