package com.sadna.group13a.domain.Interfaces;

import com.sadna.group13a.domain.Aggregates.ActiveOrder.ActiveOrder;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Repository interface for ActiveOrder aggregates.
 */
public interface IActiveOrderRepository
{
    void save(ActiveOrder order);
    Optional<ActiveOrder> findById(String orderId);

    /**
     * Finds the active DRAFT order for a user, if it exists.
     * Users should only have one active cart at a time.
     */
    Optional<ActiveOrder> findActiveByUserId(String userId);

    /**
     * Atomically finds the existing active order for a user or creates and persists
     * a new one using the supplied factory.  Implementations must guarantee that
     * concurrent callers for the same userId receive the same ActiveOrder instance.
     */
    ActiveOrder getOrCreate(String userId, Supplier<ActiveOrder> factory);

    void deleteById(String orderId);

    List<ActiveOrder> findAll();
}
