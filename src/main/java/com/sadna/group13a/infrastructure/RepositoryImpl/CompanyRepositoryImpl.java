package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class CompanyRepositoryImpl implements ICompanyRepository {

    private final ConcurrentHashMap<String, ProductionCompany> store = new ConcurrentHashMap<>();

    @Override
    public void save(ProductionCompany company) {
        store.put(company.getId(), company);
    }

    @Override
    public Optional<ProductionCompany> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<ProductionCompany> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<ProductionCompany> findByManagerId(String userId) {
        return store.values().stream()
                .filter(c -> c.getStaff().containsKey(userId))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }
}
