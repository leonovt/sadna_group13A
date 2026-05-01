package com.sadna.group13a.domain.Interfaces;

import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for retrieving immutable OrderHistory aggregates.
 */
public interface IOrderHistoryRepository 
{
    void save(OrderHistory history);
    
    Optional<OrderHistory> findById(String receiptId);
    
    /**
     * Retrieves all receipts for a specific user.
     */
    List<OrderHistory> findByUserId(String userId);
    
    /**
     * Retrieves all receipts that contain at least one item 
     * belonging to the specified company. Used for Sales Reports.
     */
    List<OrderHistory> findByCompanyId(String companyId);
}
