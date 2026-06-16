package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventJpaRepository extends JpaRepository<EventEntity, String> {
    List<EventEntity> findByCompanyId(String companyId);
    List<EventEntity> findByPublishedTrue();
    List<EventEntity> findByTitleContainingIgnoreCase(String titleFragment);
    List<EventEntity> findByCategory(String category);
}
