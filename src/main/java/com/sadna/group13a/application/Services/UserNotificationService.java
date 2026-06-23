package com.sadna.group13a.application.Services;

import com.sadna.group13a.domain.Interfaces.IUserNotificationRepository;
import com.sadna.group13a.infrastructure.UserNotification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserNotificationService {

    private final IUserNotificationRepository repository;

    public UserNotificationService(IUserNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void saveGeneral(String userId, String message) {
        repository.save(UserNotification.general(userId, message));
    }

    @Transactional
    public void saveNomination(String userId, String message, String companyId) {
        repository.save(UserNotification.nomination(userId, message, companyId));
    }

    @Transactional(readOnly = true)
    public List<UserNotification> getForUser(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void dismiss(String notificationId) {
        repository.deleteById(notificationId);
    }
}
