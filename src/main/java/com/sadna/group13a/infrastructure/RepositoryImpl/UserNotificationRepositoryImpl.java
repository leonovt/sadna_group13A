package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Interfaces.IUserNotificationRepository;
import com.sadna.group13a.infrastructure.UserNotification;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.UserNotificationEntity;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.UserNotificationJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserNotificationRepositoryImpl implements IUserNotificationRepository {

    private final UserNotificationJpaRepository jpa;

    public UserNotificationRepositoryImpl(UserNotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(UserNotification notification) {
        jpa.save(new UserNotificationEntity(
                notification.id(),
                notification.userId(),
                notification.message(),
                notification.type(),
                notification.metadata(),
                notification.createdAt()
        ));
    }

    @Override
    public List<UserNotification> findByUserIdOrderByCreatedAtDesc(String userId) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(e -> new UserNotification(
                        e.getId(), e.getUserId(), e.getMessage(),
                        e.getType(), e.getMetadata(), e.getCreatedAt()))
                .toList();
    }

    @Override
    public void deleteById(String id) {
        jpa.deleteById(id);
    }
}
