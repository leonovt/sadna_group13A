package com.sadna.group13a.domain.order;

import java.util.Optional;

/**
 * Repository interface for ActiveOrder aggregates.
 */
public interface IOrderRepository {
    void save(ActiveOrder order);
    Optional<ActiveOrder> findById(String orderId);
    
    /**
     * Finds the active DRAFT order for a user, if it exists.
     * Users should only have one active cart at a time.
     */
    Optional<ActiveOrder> findActiveByUserId(String userId);
    
    void deleteById(String orderId);
}
