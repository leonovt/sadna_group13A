package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.domain.Interfaces.IPendingNotificationRepository;
import com.sadna.group13a.infrastructure.PendingNotification;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.NotificationJpaRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.PendingNotificationEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PendingNotificationRepositoryImpl implements IPendingNotificationRepository {

    private final NotificationJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public PendingNotificationRepositoryImpl(NotificationJpaRepository jpa,
                                               @Qualifier("domainObjectMapper") ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(PendingNotification notification) {
        jpa.save(new PendingNotificationEntity(notification.id(), notification.userId(), writeJson(notification)));
    }

    @Override
    public List<PendingNotification> findByUserId(String userId) {
        return jpa.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteByUserId(String userId) {
        jpa.deleteByUserId(userId);
    }

    private String writeJson(PendingNotification notification) {
        try {
            return objectMapper.writeValueAsString(notification);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize PendingNotification " + notification.id(), e);
        }
    }

    private PendingNotification toDomain(PendingNotificationEntity entity) {
        try {
            return objectMapper.readValue(entity.getData(), PendingNotification.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize PendingNotification " + entity.getId(), e);
        }
    }
}
