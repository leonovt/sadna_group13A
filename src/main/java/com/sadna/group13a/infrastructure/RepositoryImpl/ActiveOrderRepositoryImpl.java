package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import com.sadna.group13a.domain.Interfaces.IActiveOrderRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.ActiveOrderEntity;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.ActiveOrderJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Repository
public class ActiveOrderRepositoryImpl implements IActiveOrderRepository {

    private final ActiveOrderJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public ActiveOrderRepositoryImpl(ActiveOrderJpaRepository jpa,
                                      @Qualifier("domainObjectMapper") ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void save(ActiveOrder order) {
        Optional<ActiveOrderEntity> storedEntity = jpa.findById(order.getId());
        if (storedEntity.isPresent()) {
            ActiveOrder stored = toDomain(storedEntity.get());
            if (stored.getVersion() > order.getVersion()) {
                throw new OptimisticLockException(
                        "Optimistic lock conflict for ActiveOrder " + order.getId() +
                        ": stored version " + stored.getVersion() +
                        " > incoming version " + order.getVersion());
            }
        }
        jpa.save(new ActiveOrderEntity(order.getId(), order.getUserId(), writeJson(order)));
    }

    @Override
    public Optional<ActiveOrder> findById(String orderId) {
        return jpa.findById(orderId).map(this::toDomain);
    }

    @Override
    public Optional<ActiveOrder> findActiveByUserId(String userId) {
        return jpa.findByUserId(userId).map(this::toDomain);
    }

    /**
     * Atomically returns the existing active order for {@code userId}, or creates one
     * via {@code factory}, persists it, and returns it.  Concurrent callers for the
     * same userId are serialised by the instance lock and always receive the same order.
     */
    @Override
    public synchronized ActiveOrder getOrCreate(String userId, Supplier<ActiveOrder> factory) {
        Optional<ActiveOrderEntity> existing = jpa.findByUserId(userId);
        if (existing.isPresent()) {
            return toDomain(existing.get());
        }
        ActiveOrder newOrder = factory.get();
        jpa.save(new ActiveOrderEntity(newOrder.getId(), newOrder.getUserId(), writeJson(newOrder)));
        return newOrder;
    }

    @Override
    public synchronized void deleteById(String orderId) {
        jpa.deleteById(orderId);
    }

    @Override
    public List<ActiveOrder> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    private String writeJson(ActiveOrder order) {
        try {
            return objectMapper.writeValueAsString(order);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ActiveOrder " + order.getId(), e);
        }
    }

    private ActiveOrder toDomain(ActiveOrderEntity entity) {
        try {
            return objectMapper.readValue(entity.getData(), ActiveOrder.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize ActiveOrder " + entity.getId(), e);
        }
    }
}
