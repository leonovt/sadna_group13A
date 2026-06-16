package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.domain.Aggregates.Complaint.Complaint;
import com.sadna.group13a.domain.Interfaces.IComplaintRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.ComplaintEntity;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.ComplaintJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ComplaintRepositoryImpl implements IComplaintRepository {

    private final ComplaintJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public ComplaintRepositoryImpl(ComplaintJpaRepository jpa,
                                   @Qualifier("domainObjectMapper") ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void save(Complaint complaint) {
        Optional<ComplaintEntity> storedEntity = jpa.findById(complaint.getId());
        if (storedEntity.isPresent()) {
            Complaint stored = toDomain(storedEntity.get());
            if (stored.getVersion() > complaint.getVersion()) {
                throw new OptimisticLockException(
                        "Optimistic lock conflict for Complaint " + complaint.getId() +
                        ": stored version " + stored.getVersion() +
                        " > incoming version " + complaint.getVersion());
            }
        }
        jpa.save(new ComplaintEntity(complaint.getId(), complaint.getComplainantUserId(), writeJson(complaint)));
    }

    @Override
    public Optional<Complaint> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<Complaint> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<Complaint> findByComplainantUserId(String userId) {
        return jpa.findByComplainantUserId(userId).stream().map(this::toDomain).toList();
    }

    private String writeJson(Complaint complaint) {
        try {
            return objectMapper.writeValueAsString(complaint);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Complaint " + complaint.getId(), e);
        }
    }

    private Complaint toDomain(ComplaintEntity entity) {
        try {
            return objectMapper.readValue(entity.getData(), Complaint.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize Complaint " + entity.getId(), e);
        }
    }
}
