package com.sadna.group13a.domain.Interfaces;

import com.sadna.group13a.infrastructure.PendingNotification;

import java.util.List;

public interface IPendingNotificationRepository {
    void save(PendingNotification notification);
    List<PendingNotification> findByUserId(String userId);
    void deleteByUserId(String userId);
}
