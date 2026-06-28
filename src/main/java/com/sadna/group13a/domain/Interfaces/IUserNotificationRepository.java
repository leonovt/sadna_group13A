package com.sadna.group13a.domain.Interfaces;

import com.sadna.group13a.infrastructure.UserNotification;

import java.util.List;

public interface IUserNotificationRepository {
    void save(UserNotification notification);
    List<UserNotification> findByUserIdOrderByCreatedAtDesc(String userId);
    void deleteById(String id);

    /** Removes any staff-nomination notification(s) a user holds for the given company,
     *  so an accepted/rejected invitation does not reappear on refresh (issue #368). */
    void deleteNominations(String userId, String companyId);
}
