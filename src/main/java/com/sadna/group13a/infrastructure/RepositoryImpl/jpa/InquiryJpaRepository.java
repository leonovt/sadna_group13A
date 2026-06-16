package com.sadna.group13a.infrastructure.RepositoryImpl.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryJpaRepository extends JpaRepository<InquiryEntity, String> {
    List<InquiryEntity> findByCompanyId(String companyId);
    List<InquiryEntity> findByFromUserId(String fromUserId);
}
