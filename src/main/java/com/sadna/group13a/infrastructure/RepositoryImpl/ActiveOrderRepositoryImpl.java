package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ActiveOrderRepositoryImpl implements IActiveOrderRepository {

    private final ConcurrentHashMap<String, ActiveOrder> store = new ConcurrentHashMap<>();

    /**
     * Persists the order.  Detects optimistic-lock conflicts when the caller holds a
     * different object instance than what is currently in the store (e.g., after
     * deserialization or a future JPA migration): if the stored version is newer than
     * the incoming version, the update is rejected.
     */
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
    }

    @Override
    public Optional<ActiveOrder> findById(String orderId) {
        return Optional.ofNullable(store.get(orderId));
    }

    @Override
    public Optional<ActiveOrder> findActiveByUserId(String userId) {
        return store.values().stream()
                .filter(o -> o.getUserId().equals(userId))
                .findFirst();
    }

    @Override
    public void deleteById(String orderId) {
        store.remove(orderId);
    }

    @Override
    public List<ActiveOrder> findAll() {
        return new ArrayList<>(store.values());
    }
}
