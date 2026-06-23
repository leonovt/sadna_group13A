package com.sadna.group13a.domain.Interfaces;

import com.sadna.group13a.infrastructure.UserNotification;

import java.util.List;

public interface IUserNotificationRepository {
    void save(UserNotification notification);
    List<UserNotification> findByUserIdOrderByCreatedAtDesc(String userId);
    void deleteById(String id);
}
