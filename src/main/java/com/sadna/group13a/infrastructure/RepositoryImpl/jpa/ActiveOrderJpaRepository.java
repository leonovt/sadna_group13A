package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActiveOrderJpaRepository extends JpaRepository<ActiveOrderEntity, String> {
    Optional<ActiveOrderEntity> findByUserId(String userId);
}
