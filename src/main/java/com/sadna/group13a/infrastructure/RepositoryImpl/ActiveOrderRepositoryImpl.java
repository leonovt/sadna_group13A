package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Repository
public class ActiveOrderRepositoryImpl implements IActiveOrderRepository {

    private final ConcurrentHashMap<String, ActiveOrder> store = new ConcurrentHashMap<>();
    // Secondary index: userId → orderId. Kept consistent with store under the same lock.
    private final ConcurrentHashMap<String, String> userIndex = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(ActiveOrder order) {
        ActiveOrder stored = store.get(order.getId());
        if (stored != null && stored != order && stored.getVersion() > order.getVersion()) {
            throw new OptimisticLockException(
                    "Optimistic lock conflict for ActiveOrder " + order.getId() +
                    ": stored version " + stored.getVersion() +
                    " > incoming version " + order.getVersion());
        }
        store.put(order.getId(), order);
        userIndex.put(order.getUserId(), order.getId());
    }

    @Override
    public Optional<ActiveOrder> findById(String orderId) {
        return Optional.ofNullable(store.get(orderId));
    }

    @Override
    public Optional<ActiveOrder> findActiveByUserId(String userId) {
        String orderId = userIndex.get(userId);
        return orderId == null ? Optional.empty() : Optional.ofNullable(store.get(orderId));
    }

    /**
     * Atomically returns the existing active order for {@code userId}, or creates one
     * via {@code factory}, persists it, and returns it.  Concurrent callers for the
     * same userId are serialised by the instance lock and always receive the same order.
     */
    @Override
    public synchronized ActiveOrder getOrCreate(String userId, Supplier<ActiveOrder> factory) {
        String orderId = userIndex.get(userId);
        if (orderId != null) {
            ActiveOrder existing = store.get(orderId);
            if (existing != null) {
                return existing;
            }
        }
        ActiveOrder newOrder = factory.get();
        store.put(newOrder.getId(), newOrder);
        userIndex.put(userId, newOrder.getId());
        return newOrder;
    }

    @Override
    public synchronized void deleteById(String orderId) {
        ActiveOrder removed = store.remove(orderId);
        if (removed != null) {
            userIndex.remove(removed.getUserId());
        }
    }

    @Override
    public List<ActiveOrder> findAll() {
        return new ArrayList<>(store.values());
    }
}
