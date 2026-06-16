package com.sadna.group13a.domain.Interfaces;

import com.sadna.group13a.domain.Aggregates.Inquiry.Inquiry;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository for the {@link Inquiry} aggregate. Implemented in the
 * infrastructure layer (Spring Data JPA).
 */
public interface IInquiryRepository {

    void save(Inquiry inquiry);

    Optional<Inquiry> findById(String id);

    List<Inquiry> findByCompanyId(String companyId);

    List<Inquiry> findByFromUserId(String userId);
}
