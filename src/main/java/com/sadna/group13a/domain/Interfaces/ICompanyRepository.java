package com.sadna.group13a.domain.Interfaces;

import java.util.List;
import java.util.Optional;

import com.sadna.group13a.domain.Aggregates.Company.Company;

/**
 * Repository interface for ProductionCompany aggregates.
 */
public interface ICompanyRepository 
{

    void save(Company company);

    Optional<Company> findById(String id);

    List<Company> findAll();
    
    /**
     * Finds all companies where the user has a role (OWNER or MANAGER).
     */
    List<Company> findByManagerId(String userId);

    void deleteById(String id);
}
