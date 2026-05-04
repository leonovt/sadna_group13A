package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ActiveOrderRepositoryImpl implements IActiveOrderRepository {

    private final ConcurrentHashMap<String, ActiveOrder> store = new ConcurrentHashMap<>();

    @Override
    public void save(ActiveOrder order) {
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
}
