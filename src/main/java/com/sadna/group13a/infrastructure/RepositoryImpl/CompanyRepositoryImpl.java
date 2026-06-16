package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.CompanyEntity;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.CompanyJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CompanyRepositoryImpl implements ICompanyRepository {

    private final CompanyJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public CompanyRepositoryImpl(CompanyJpaRepository jpa,
                                  @Qualifier("domainObjectMapper") ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void save(ProductionCompany company) {
        Optional<CompanyEntity> storedEntity = jpa.findById(company.getId());
        if (storedEntity.isPresent()) {
            ProductionCompany stored = toDomain(storedEntity.get());
            if (stored.getVersion() >= company.getVersion()) {
                throw new OptimisticLockException(
                        "Optimistic lock conflict for ProductionCompany " + company.getId() +
                        ": stored version " + stored.getVersion() +
                        " > incoming version " + company.getVersion());
            }
        }
        jpa.save(new CompanyEntity(company.getId(), writeJson(company)));
    }

    @Override
    public Optional<ProductionCompany> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<ProductionCompany> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<ProductionCompany> findByManagerId(String userId) {
        return jpa.findAll().stream()
                .map(this::toDomain)
                .filter(c -> c.getStaff().containsKey(userId))
                .toList();
    }

    @Override
    public void deleteById(String id) {
        jpa.deleteById(id);
    }

    private String writeJson(ProductionCompany company) {
        try {
            return objectMapper.writeValueAsString(company);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ProductionCompany " + company.getId(), e);
        }
    }

    private ProductionCompany toDomain(CompanyEntity entity) {
        try {
            return objectMapper.readValue(entity.getData(), ProductionCompany.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize ProductionCompany " + entity.getId(), e);
        }
    }
}
