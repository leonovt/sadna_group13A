package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RaffleJpaRepository extends JpaRepository<RaffleEntity, String> {
    List<RaffleEntity> findByEventId(String eventId);
    List<RaffleEntity> findByCompanyId(String companyId);
}
