package com.sadna.group13a.domain.Interfaces;

import com.sadna.group13a.domain.Aggregates.Complaint.Complaint;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository for the {@link Complaint} aggregate. Implemented in the
 * infrastructure layer (Spring Data JPA).
 */
public interface IComplaintRepository {

    void save(Complaint complaint);

    Optional<Complaint> findById(String id);

    List<Complaint> findAll();

    List<Complaint> findByComplainantUserId(String userId);
}
