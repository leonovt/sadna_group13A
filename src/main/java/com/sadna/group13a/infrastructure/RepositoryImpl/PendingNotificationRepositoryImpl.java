package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Interfaces.IPendingNotificationRepository;
import com.sadna.group13a.infrastructure.PendingNotification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class PendingNotificationRepositoryImpl implements IPendingNotificationRepository {

    private final Map<String, List<PendingNotification>> store = new ConcurrentHashMap<>();

    @Override
    public void save(PendingNotification notification) {
        store.computeIfAbsent(notification.userId(), k -> new CopyOnWriteArrayList<>())
             .add(notification);
    }

    @Override
    public List<PendingNotification> findByUserId(String userId) {
        return new ArrayList<>(store.getOrDefault(userId, List.of()));
    }

    @Override
    public void deleteByUserId(String userId) {
        store.remove(userId);
    }
}
