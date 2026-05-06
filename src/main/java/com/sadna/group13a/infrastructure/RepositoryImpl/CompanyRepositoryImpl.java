package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Company.ProductionCompany;
import com.sadna.group13a.domain.Interfaces.ICompanyRepository;
import com.sadna.group13a.domain.shared.OptimisticLockException;
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
        ProductionCompany stored = store.get(company.getId());
        if (stored != null && stored.getVersion() > company.getVersion()) {
            throw new OptimisticLockException(
                    "Optimistic lock conflict for ProductionCompany " + company.getId() +
                    ": stored version " + stored.getVersion() +
                    " > incoming version " + company.getVersion());
        }
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
