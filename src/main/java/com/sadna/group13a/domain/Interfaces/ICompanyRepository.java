package com.sadna.group13a.domain.Interfaces;

import java.util.List;
import java.util.Optional;

import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;

/**
 * Repository interface for ProductionCompany aggregates.
 */
public interface ICompanyRepository {

    void save(ProductionCompany company);

    Optional<ProductionCompany> findById(String id);

    List<ProductionCompany> findAll();
    
    /**
     * Finds all companies where the user has a role (OWNER or MANAGER).
     */
    List<ProductionCompany> findByManagerId(String userId);

    void deleteById(String id);
}