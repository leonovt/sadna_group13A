package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.OrderHistoryEntity;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.OrderHistoryJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OrderHistoryRepositoryImpl implements IOrderHistoryRepository {

    private final OrderHistoryJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public OrderHistoryRepositoryImpl(OrderHistoryJpaRepository jpa,
                                       @Qualifier("domainObjectMapper") ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(OrderHistory history) {
        jpa.save(new OrderHistoryEntity(history.getReceiptId(), history.getUserId(), writeJson(history)));
    }

    @Override
    public Optional<OrderHistory> findById(String receiptId) {
        return jpa.findById(receiptId).map(this::toDomain);
    }

    @Override
    public List<OrderHistory> findByUserId(String userId) {
        return jpa.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<OrderHistory> findByCompanyId(String companyId) {
        return jpa.findAll().stream()
                .map(this::toDomain)
                .filter(h -> h.containsItemFromCompany(companyId))
                .toList();
    }

    @Override
    public List<OrderHistory> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    private String writeJson(OrderHistory history) {
        try {
            return objectMapper.writeValueAsString(history);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize OrderHistory " + history.getReceiptId(), e);
        }
    }

    private OrderHistory toDomain(OrderHistoryEntity entity) {
        try {
            return objectMapper.readValue(entity.getData(), OrderHistory.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize OrderHistory " + entity.getReceiptId(), e);
        }
    }
}
