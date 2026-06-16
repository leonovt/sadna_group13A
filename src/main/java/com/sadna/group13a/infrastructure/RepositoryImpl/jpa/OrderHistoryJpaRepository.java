package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderHistoryJpaRepository extends JpaRepository<OrderHistoryEntity, String> {
    List<OrderHistoryEntity> findByUserId(String userId);
}
