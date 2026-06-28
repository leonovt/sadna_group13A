package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserNotificationJpaRepository extends JpaRepository<UserNotificationEntity, String> {

    List<UserNotificationEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Deletes all notifications of a given type for a user whose metadata matches (e.g. all
     *  STAFF_NOMINATION notifications for one company). Used to clear an invitation once the
     *  user has accepted or rejected it, so it does not reappear on refresh (issue #368). */
    long deleteByUserIdAndTypeAndMetadata(String userId, String type, String metadata);
}
