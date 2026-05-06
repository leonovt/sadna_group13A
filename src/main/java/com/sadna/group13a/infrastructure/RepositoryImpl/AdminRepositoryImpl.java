package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class AdminRepositoryImpl implements IAdminRepository {

    private final ConcurrentHashMap<String, Admin> store = new ConcurrentHashMap<>();

    @Override
    public void save(Admin admin) {
        store.put(admin.getId(), admin);
    }

    @Override
    public Optional<Admin> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Admin> findByUserId(String userId) {
        return store.values().stream()
                .filter(a -> a.getUserId().equals(userId))
                .findFirst();
    }

    @Override
    public List<Admin> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }

    @Override
    public boolean hasAtLeastOneAdmin() {
        return !store.isEmpty();
    }
}
