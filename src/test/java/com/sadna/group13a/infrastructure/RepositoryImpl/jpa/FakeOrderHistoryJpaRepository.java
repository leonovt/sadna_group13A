package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import java.util.List;

public class FakeOrderHistoryJpaRepository extends AbstractFakeJpaRepository<OrderHistoryEntity, String> implements OrderHistoryJpaRepository {

    public FakeOrderHistoryJpaRepository() {
        super(OrderHistoryEntity::getReceiptId);
    }

    @Override
    public List<OrderHistoryEntity> findByUserId(String userId) {
        return findAll().stream().filter(e -> e.getUserId().equals(userId)).toList();
    }
}
