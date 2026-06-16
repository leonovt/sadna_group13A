package com.sadna.group13a.infrastructure.RepositoryImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadna.group13a.domain.Aggregates.Admin.Admin;
import com.sadna.group13a.domain.Interfaces.IAdminRepository;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.AdminEntity;
import com.sadna.group13a.infrastructure.RepositoryImpl.jpa.AdminJpaRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AdminRepositoryImpl implements IAdminRepository {

    private final AdminJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public AdminRepositoryImpl(AdminJpaRepository jpa,
                                @Qualifier("domainObjectMapper") ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(Admin admin) {
        jpa.save(new AdminEntity(admin.getId(), admin.getUserId(), writeJson(admin)));
    }

    @Override
    public Optional<Admin> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Admin> findByUserId(String userId) {
        return jpa.findByUserId(userId).map(this::toDomain);
    }

    @Override
    public List<Admin> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(String id) {
        jpa.deleteById(id);
    }

    @Override
    public boolean hasAtLeastOneAdmin() {
        return jpa.count() > 0;
    }

    private String writeJson(Admin admin) {
        try {
            return objectMapper.writeValueAsString(admin);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Admin " + admin.getId(), e);
        }
    }

    private Admin toDomain(AdminEntity entity) {
        try {
            return objectMapper.readValue(entity.getData(), Admin.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize Admin " + entity.getId(), e);
        }
    }
}
