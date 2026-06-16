package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintJpaRepository extends JpaRepository<ComplaintEntity, String> {
    List<ComplaintEntity> findByComplainantUserId(String complainantUserId);
}
