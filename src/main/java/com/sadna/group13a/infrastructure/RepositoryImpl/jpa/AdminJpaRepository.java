package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminJpaRepository extends JpaRepository<AdminEntity, String> {
    Optional<AdminEntity> findByUserId(String userId);
}
