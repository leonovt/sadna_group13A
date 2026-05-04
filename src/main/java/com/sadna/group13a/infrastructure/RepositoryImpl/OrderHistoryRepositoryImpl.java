package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.OrderHistory.OrderHistory;
import com.sadna.group13a.domain.Interfaces.IOrderHistoryRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class OrderHistoryRepositoryImpl implements IOrderHistoryRepository {

    private final ConcurrentHashMap<String, OrderHistory> store = new ConcurrentHashMap<>();

    @Override
    public void save(OrderHistory history) {
        store.put(history.getReceiptId(), history);
    }

    @Override
    public Optional<OrderHistory> findById(String receiptId) {
        return Optional.ofNullable(store.get(receiptId));
    }

    @Override
    public List<OrderHistory> findByUserId(String userId) {
        return store.values().stream()
                .filter(h -> h.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderHistory> findByCompanyId(String companyId) {
        return store.values().stream()
                .filter(h -> h.containsItemFromCompany(companyId))
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderHistory> findAll() {
        return new ArrayList<>(store.values());
    }
}
