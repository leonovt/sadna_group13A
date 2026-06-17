package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.domain.Aggregates.Inquiry.Inquiry;
import com.sadna.group13a.domain.Interfaces.IInquiryRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.InquiryEntity;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.InquiryJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InquiryRepositoryImpl implements IInquiryRepository {

    private final InquiryJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public InquiryRepositoryImpl(InquiryJpaRepository jpa,
                                 @Qualifier("domainObjectMapper") ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void save(Inquiry inquiry) {
        Optional<InquiryEntity> storedEntity = jpa.findById(inquiry.getId());
        if (storedEntity.isPresent()) {
            Inquiry stored = toDomain(storedEntity.get());
            if (stored.getVersion() > inquiry.getVersion()) {
                throw new OptimisticLockException(
                        "Optimistic lock conflict for Inquiry " + inquiry.getId() +
                        ": stored version " + stored.getVersion() +
                        " > incoming version " + inquiry.getVersion());
            }
        }
        jpa.save(new InquiryEntity(inquiry.getId(), inquiry.getCompanyId(), inquiry.getFromUserId(), writeJson(inquiry)));
    }

    @Override
    public Optional<Inquiry> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<Inquiry> findByCompanyId(String companyId) {
        return jpa.findByCompanyId(companyId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Inquiry> findByFromUserId(String userId) {
        return jpa.findByFromUserId(userId).stream().map(this::toDomain).toList();
    }

    private String writeJson(Inquiry inquiry) {
        try {
            return objectMapper.writeValueAsString(inquiry);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Inquiry " + inquiry.getId(), e);
        }
    }

    private Inquiry toDomain(InquiryEntity entity) {
        try {
            return objectMapper.readValue(entity.getData(), Inquiry.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize Inquiry " + entity.getId(), e);
        }
    }
}
